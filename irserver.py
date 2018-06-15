# A server for handling image classification and training
# requests. Runs as a daemon, listening for incoming requests
# and dispatching them to threads for training or classification.

import os, sys, time, socket, select, threading
from datetime import datetime
from daemon import Daemon
import numpy as np
import cv2

REFUSE_IR_REQ = "Request not recognized. Valid requests are Train|Classify"
IR_REQ_ST = 'Stream'
IR_REQ_MP4ST = 'MP4Stream'
IR_REQ_TR = 'Train'
IR_REQ_CL = 'Classify'
IR_REQ_SS = 'Slideshow'
IR_READY = "Ready"
CURR_FILE_ID = 0
SERVER_IP = '0.0.0.0'

class IRDispatcher(Daemon):

    def run(self):
        self.fileID = 0
        self.AVI_ID = 0
        self.init_ir_server()

    def next_filename(self, avi=None):
        now = datetime.now()
        folderPath = str(now.year) + "_" + str(now.month) + "_" + str(now.day)

        if not os.path.isdir(folderPath):
            try:
                os.makedirs(folderPath, mode=0777)
            except Exception as e:
                print(e)
                exit()

            self.fileID = 0
            self.AVI_ID = 0

        if avi is None:
            self.fileID += 1
            fileName = str(self.fileID).zfill(19) + ".jpg"
            return folderPath + "\\" + fileName
        else:
            self.AVI_ID += 1
            fileName = str(self.AVI_ID).zfill(19) + ".264"
            return folderPath + "\\" + fileName

    def init_ir_server(self):
        self.fileID = 0
        HOST = ''
        PORT = 50505

        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.bind((SERVER_IP, 50505))
            print(s.getsockname())
            s.listen(3)
        except Exception as e:
            print("Socket failed to init.")
            print(e)
            exit()

        while True:
            try:
                conn, addr = s.accept()
                print("Accepted")
                data = conn.recv(1024)
                print(data)
            except Exception as e:
                print("Accept and connect failure.")
                print(e)
                continue

            if IR_REQ_TR in data:
                path = self.next_filename()
                t = threading.Thread(target=train_thread, args=(conn, path))
                t.start()
            elif IR_REQ_CL in data:
                path = self.next_filename()
                t = threading.Thread(target=classify_thread, args=(conn, path))
                t.start()
            elif IR_REQ_ST in data:
                path = self.next_filename('avi')
                t = threading.Thread(target=stream_thread, args=(conn, path))
                t.start()
            elif IR_REQ_SS in data:
                path = self.next_filename()
                t = threading.Thread(target=slideshow_thread, args=(conn, path))
                t.start()
            elif IR_REQ_MP4ST in data:
                path = self.next_filename()
                t = threading.Thread(target=mp4stream_thread, args=(conn, path))
                t.start()
            else:
                try:
                    conn.sendall(REFUSE_IR_REQ)
                    conn.close()
                except Exception as e:
                    print("Refusing connection.")
                    print(e)
                    continue

def mp4stream_thread(conn, filepath):

    class ReadState(Enum):
        PPSSEARCH = 0
        FRAMESEARCH = 1
        FRAMEREAD = 2

    print("mp4stream")
    f = open(filepath, 'wb+')
    cap = cv2.VideoCapture(filepath)
    print(cap.isOpened)
    state = ReadState.PPSSEARCH

    try:
        OOBsInit = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        OOBsInit.bind((SERVER_IP, 50506))
        OOBs_info = OOBsInit.getsockname()
        OOBsInit.listen(3)

        print(OOBs_info[1])
        conn.sendall(str(OOBs_info[1]))
        OOBs, addr = OOBsInit.accept()
        conn.sendall(IR_READY)
    except Exception as e:
        print("Error creating communication socket")
        print(e)
        exit()    # save incoming video file
        
    print("reading incoming file")
    frameSize = 0
    try:
        while True:
            msg = OOBs.recv()

            if state == ReadState.PPSSEARCH:
                if msg.find("avc") >= 0:
                    msg = msg[msg.find(b'\x76\x63\x31'):]
                    msg = msg[msg.find(b'\x42\x80'):]
                    PPS = msg[:msg.find(b'\x01\x00\04')]
                    SPS = msg[msg.find(b'\x01\x00\04\x68') + 3:msg.find(b'\x01\x00\04\x68') + 7]
                    f.write(b'\x00\x00\x00\x01')
                    f.write(PPS)
                    f.write(b'\x00\x00\x00\x01')
                    f.write(SPS)
                    state = ReadState.FRAMESEARCH
            if state == ReadState.FRAMESEARCH:
                if "mdat" in msg:
                    msg = msg[msg.find("mdat") + 4:]
                    state = ReadState.FRAMEREAD

            if state == ReadState.FRAMEREAD:
                #TODO get next frame
                if frameSize == 0:
                    f.write(b'\x00\x00\x00\x01')
                    framesize = (ord(msg[0]) << 24) + (ord(msg[1]) << 16) + (ord(msg[2]) << 8) + (ord(msg[3]))
                    msg = msg[4:]

                    ret, frame = cap.read()
                    if ret:
                        try:
                            cv2.imshow('frame',frame)
                            if cv2.waitKey(1) & 0xFF == ord('q'):
                                break
                        except Exception as e:
                            fCount = fCount

                else:
                    writeAmount = min(frameSize, len(msg))
                    f.write(msg[:writeAmount])
                    frameSize -= writeAmount

    except Exception as e:
        print("Error recieving image.")
        print(e)
        f.close()
        cap.release()

    conn.close()

# recieve H264 encoded stream and save as AVI
def stream_thread(conn, filepath):
    print("stream")
    print(filepath)
    f = open(filepath, 'wb+')
    cap = cv2.VideoCapture(filepath)
    print(cap.isOpened)
    try:
        OOBsInit = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        OOBsInit.bind((SERVER_IP, 50506))
        OOBs_info = OOBsInit.getsockname()
        OOBsInit.listen(3)

        print(OOBs_info[1])
        conn.sendall(str(OOBs_info[1]))
        OOBs, addr = OOBsInit.accept()
        conn.sendall(IR_READY)
    except Exception as e:
        print("Error creating communication socket")
        print(e)
        exit()

    readset = [conn, OOBs]
    writeset = []
    errset = []

    # save incoming video file
    print("reading incoming file")
    fCount = 0
    try:
        while True:
            readable, writable, erronious = select.select(readset, writeset, errset)
            if OOBs in readable:
                msg = OOBs.recv(1024)
                print(msg)
                if "DONE" in msg:
                    break

                OOBs.sendall("RECEIVED")
            if conn in readable:
                data = conn.recv(1024)
                f.write(data)

                #if fCount < 3:
                #    fCount += 1
                #else:
                #    ret, frame = cap.read()
                #    if ret:
                #        try:
                #            cv2.imshow('frame',frame)
                #            if cv2.waitKey(1) & 0xFF == ord('q'):
                #                break
                #        except Exception as e:
                #            fCount = fCount

    except Exception as e:
        print("Error recieving image.")
        print(e)
        f.close()
        cap.release()

    conn.close()
                    
# recieve and process an image training request
def classify_thread(conn, filepath):
    f = open(filepath, 'wb+')

    # save incoming image file
    try:
        conn.sendall(IR_READY)
        while True:
            data = conn.recv(1024)
            if not data or "Done" in data:
                if len(data) > 4:
                    f.write(data[0:len(data) - 6])
                break
            f.write(data)
        f.close()
        conn.sendall("RECEIVED")
    except Exception as e:
        print("Error recieving image.")
        print(e)
        f.close()
        conn.close()
        return

    # pass image to classifier
    prediction = relay_classify_req(filepath)
    if prediction is None:
        prediction = "Undefined"
    conn.sendall(prediction)

    conn.close()

# recieve and process a stream of YUV images
def slideshow_thread(conn, filepath):
    print("slideshow")
    f = open(filepath, 'wb+')
    fRead = open(filepath, "rb+")

    try:
        conn.sendall(IR_READY)
        datasize = 0
        while True:
            data = conn.recv(1024)
            f.write(data)

            #print(data)

            if datasize < 463000:
                datasize += len(data)
            else:
                print("frame recieved")
                datasize = datasize % 460800
                w = 640
                h = 480
	
                byteArray = fRead.read(460800)
                byteArray = np.frombuffer(byteArray, dtype=np.uint8)
                byteArray = np.reshape(byteArray, (h*3/2, w))

                RGBMatrix = cv2.cvtColor(byteArray, cv2.COLOR_YUV2BGR_NV21)

                cv2.imshow('frame', RGBMatrix)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break
                
                conn.sendall("RECEIVED")

        f.close()
    except Exception as e:
        print("Error recieving image.")
        print(e)
        f.close()
        conn.close()
        return

    conn.close()

# recieve and process an image classification request
def train_thread(conn, filepath):
    f = open(filepath, 'wb+')

    # save incoming image file
    try:
        conn.sendall(IR_READY)
        while True:
            data = conn.recv(1024)
            if not data or "Done" in data:
                break
            f.write(data)
        f.close()
    except Exception as e:
        print("Error recieving image.")
        f.close()
        conn.close()
        return

    conn.close()

# pass an image to a classifier
# implementation will differ
def relay_classify_req(filepath):
    # NULL Method
    return None

def YUVtoRGB(filename):
    w = 640
    h = 480
	
    file = open(filename, "rb+")
    byteArray = file.read(460800)
    byteArray = np.frombuffer(byteArray, dtype=np.uint8)
    byteArray = np.reshape(byteArray, (h*3/2, w))

    RGBMatrix = cv2.cvtColor(byteArray, cv2.COLOR_YUV2BGR_NV21)
    return RGBMatrix

# init daemon
if __name__ == "__main__":
    daemon = IRDispatcher('/tmp/IRDispatcher.pid')
    daemon.run()