# A client for handling image classification and training
# requests.
import os, sys, time, socket, collections, itertools, cv2, pickle, numpy, scipy, scipy.sparse
from datetime import datetime
from time import sleep

IR_REQ_ST = 'Stream'
IR_REQ_TR = 'Train'
IR_REQ_CL = 'Classify'
IR_READY = "Ready"
SERVER_IP = '10.22.17.30'

class IRClient:

    def __init__(self):
        self.init = 1

    def train_req(self, imgpath):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	    s.settimeout(1.5)
            s.connect((SERVER_IP, 50505))
            s.sendall(IR_REQ_TR)
        except Exception as e:
            print("Socket init error")
            print(e)
            s.close()
            return

        try:
            if IR_READY in s.recv(1024):
                # send image
                f = open(imgpath, "rb")
                while True:
                    data = f.read(1024)
                    if data == '':
                        break
                    s.sendall(data)
		f.close()
                s.sendall("Done")
        except Exception as e:
            print("Image failed to send.")
            print(e)
	    return None

        s.close()

    def classify_req(self, imgpath):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
	    s.settimeout(1.5)
            s.connect((SERVER_IP, 50505))
            s.sendall(IR_REQ_CL)
        except Exception as e:
            print("Socket init error")
            print(e)
            s.close()
            return

        try:
            if IR_READY in s.recv(1024):
                # send image
                f = open(imgpath, "rb")
                while True:
                    data = f.read(1024)
                    if data == '':
                        break
                    s.sendall(data)
		f.close()
                s.sendall("Done")

        except Exception as e:
            print("Image failed to send.")
            print(e)
	    return None

        try:
            prediction = s.recv(1024)
        except Exception as e:
            prediction = "Failed"
            print("Prediction failed.")
            print(e)

        s.close()

        return prediction

def streamreq():
    cap = cv2.VideoCapture(0)
    # create format converter
    # see https://github.com/cisco/openh264/releases/tag/v1.6.0 for appropriate h264 lib
    fourCC =  cv2.VideoWriter_fourcc('H','2','6','4')
    encoder = cv2.VideoWriter('out.avi',fourCC,10.0,(640,480))


    if cap.isOpened():
        rval, frame = cap.read()
    else:
        print("Webcam not found")
        return

    try:
        encoder.write(frame)
        f = open('out.avi', 'rb')
    except Exception as e:
        print('error encoding stream')
        print(e)
        return

    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        OOBs = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.settimeout(1.5)
        OOBs.settimeout(3.0)
        s.connect((SERVER_IP, 50505))
        s.sendall(IR_REQ_ST)
    except Exception as e:
        print("Socket init error")
        print(e)
        s.close()
        return

    try:
        resp =  s.recv(1024)
        OOBs.connect((SERVER_IP, int(resp)))
        resp =  s.recv(1024)
        st = datetime.now()

        tics = 0
        if IR_READY in resp:
            while rval:
                st = datetime.now()
                tics +=1
                if tics == 100:
                    break;

                msg = f.read()
                s.sendall(msg)
                OOBs.sendall("SENT")
                
                #check for response, report send time
                resp = OOBs.recv(1024)
                ed = datetime.now()
                print(resp)
                print('time: (hh:mm:ss:ms) {}'.format(ed - st))

                # get next image frame
                rval, frame = cap.read()
                encoder.write(frame)

                # check for key interrupt
                key = cv2.waitKey(10)
                if key == 27:
                    s.sendall('Done')
                    break
        else:
            print("Error: server not ready")
            print(resp)
    except Exception as e:
        print("Error sending stream.")
        print(e)
        
    # Release everything if job is finished 
    cap.release()
    encoder.release()
    cv2.destroyAllWindows()

if __name__ == "__main__":
    test = IRClient()
    cwd = os.getcwd()

    d = datetime.now()
    totalTime = d - d
    maxTime = d - d
    for i in range(100):
        start = datetime.now()
        testRes = test.classify_req(cwd + "/test.jpg")
        end = datetime.now()

        if maxTime < (end - start):
            maxTime = (end - start)
        totalTime += (end - start)

	sleep(0.5)
        #if testRes is not None:
        #    print("found " + testRes)
        #    print("in (hh:mm:ss.ms) {}".format(end-start))
    print('average time: (hh:mm:ss:ms) {}'.format(totalTime/100))
    print('longest time: (hh:mm:ss:ms) {}'.format(maxTime))
