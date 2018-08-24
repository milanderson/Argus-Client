# A server for handling image classification and training
# requests. Runs as a daemon, listening for incoming requests
# and dispatching them to threads for training or classification.

import os, sys, re, time, socket, select, threading, requests, Boxify
from datetime import datetime
from daemon import Daemon
from random import *
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
GPU_SERV_CLASS_ADDR = "http://127.0.0.1:8000/api/classify"
GPU_SERV_TRAIN_ADDR = "http://127.0.0.1:8000/api/train"
CLASSLIST = 'classlist.txt'

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
	
	    sReply = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	    sReply.bind((SERVER_IP, 50506))
	
            s.listen(3)
	    sReply.listen(3)
	    readList = [s, sReply]
	
	    connList = []

        except Exception as e:
            print("Socket failed to init.")
            print(e)
            exit()

        while True:
	    readable, writable, errored = select.select(readList, [], [])
		
	    if sReply in readable:
		try:
		    replyConn, addr = sReply.accept()
		    if len(connList) > 0:
		    	conn = connList.pop(0)
		    	t = threading.Thread(target=slideshow_thread, args=(conn, replyConn))
                    	t.start()
		except Exception as e:
		    print("Error accepting reply socket.")
		    print(e)
		    continue
		
            if s in readable:
	        try:
                    conn, addr = s.accept()
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
                    connList.append(conn)
		    if len(connList) > 10:
		        connList.pop(0).close()
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
def slideshow_thread(conn, replyConn):
    print("slideshow")
    classListFile = open(CLASSLIST, "r")
    classList = classListFile.readlines()
    readList =[conn, replyConn]
    frame = None
    byteArray = ""
    
    w = 640
    h = 480

    try:
        conn.sendall(IR_READY)
        byteArray = ""
        while True:
            readable, writable, errored = select.select(readList, [], [])

            if replyConn in readable:
                frameClass = replyConn.recv(1024)

                if frameClass == '':
                	raise ValueError('Failed to read reply socket.')

                print("reply: ", frameClass)
		
                match = re.search(frameClass, classList[0], re.I)
                classNum = 0
                while match is not None and classNum < len(classList):
                    classNum += 1
                    match = re.search(frameClass, classList[classNum], re.I)
                if match is not None:
                    RGBMatrix = cv2.cvtColor(frame, cv2.COLOR_YUV2BGR_NV21)
			
                    relay_train_req(string(classNum).zfill(4), RGBMatrix)
		    frameFile.close()

            if conn in readable:
                data = conn.recv(460800)

                if len(byteArray) < 460800:
                    byteArray = byteArray + data
                else:
                    print("full frame")
                
                    frame = byteArray[:460800]
                    byteArray = byteArray[460800:]

                    frame = np.frombuffer(frame, dtype=np.uint8)
                    frame = np.reshape(frame, (h*3/2, w))

                    RGBMatrix = cv2.cvtColor(frame, cv2.COLOR_YUV2BGR_NV21)

                    #cv2.imshow('frame', RGBMatrix)
                    #if cv2.waitKey(1) & 0xFF == ord('q'):
                    #    break

                    for guess in relay_classify_req(RGBMatrix):
                        conn.sendall(guess + ",")
                    
                    conn.sendall("RECEIVED")
			
    except Exception as e:
        print("Error recieving image.")
        print(e)
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
def relay_classify_req(img):
    ret, jpg = cv2.imencode(".jpg", img)
    responses = list()

    try:
        r = requests.post(GPU_SERV_CLASS_ADDR, jpg.tostring())
        if r.status_code == 200:
            data = r.json()
    
            for i in range(min(len(data), 3)):
                endPt = data[i]['label'].find(",")
                if endPt == -1:
                    endPt = len(data[i]['label'])

                responses.append(data[i]['label'][10:endPt])
    except Exception as e:
        print(e)
    return responses

def relay_train_req(classNum, img):
    ret, jpg = cv2.imencode(".jpg", img)
    try:
        r = requests.post(GPU_SERV_train_ADDR, classNum + jpg.tostring())
        print(r.status_code)
    except Exception as e:
        print(e)
	

def YUVtoRGB(filename):
    w = 640
    h = 480
	
    file = open(filename, "rb+")
    byteArray = file.read(460800)
    byteArray = np.frombuffer(byteArray, dtype=np.uint8)
    byteArray = np.reshape(byteArray, (h*3/2, w))

    RGBMatrix = cv2.cvtColor(byteArray, cv2.COLOR_YUV2BGR_NV21)
    return RGBMatrix

# pass an image to a classifier
def relay_frame_classify_req(frame):
    return Boxify.boxify(frame)

# init daemon
if __name__ == "__main__":
    daemon = IRDispatcher('/tmp/IRDispatcher.pid')
    daemon.run()
