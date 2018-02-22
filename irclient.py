# A client for handling image classification and training
# requests.
import os, sys, time, socket
from datetime import datetime
from time import sleep

IR_REQ_TR = 'Train'
IR_REQ_CL = 'Classify'
IR_READY = 'Ready'
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
