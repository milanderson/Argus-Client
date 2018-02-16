# A client for handling image classification and training
# requests.
import os, sys, time, socket
from datetime import datetime

IR_REQ_TR = 'Train'
IR_REQ_CL = 'Classify'
IR_READY = 'Ready'

class IRClient:

    def __init__(self):
        self.init = 1

    def train_req(self, imgpath):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect(('localhost', 50505))
            s.sendall(IR_REQ_TR)
        except Exception as e:
            print("Socket init error")
            print(e)
            s.close()
            return

        try:
            if IR_READY in s.recv(1024):
                # send image
                f = open(imgpath)
                while True:
                    data = f.read(1024)
                    if data == '':
                        break
                    s.sendall(data)
                s.sendall("Done")
        except Exception as e:
            print("Image failed to send.")
            print(e)

        s.close()

    def classify_req(self, imgpath):
        try:
            s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            s.connect(('localhost', 50505))
            s.sendall(IR_REQ_TR)
        except Exception as e:
            print("Socket init error")
            print(e)
            s.close()
            return

        prediction = "Failed"
        try:
            if IR_READY in s.recv(1024):
                # send image
                f = open(imgpath)
                while True:
                    data = f.read(1024)
                    if data == '':
                        break
                    s.sendall(data)
                s.sendall("Done")

        except Exception as e:
            print("Image failed to send.")
            print(e)

        try:
            prediction = s.recv(1024)
        except Exception as e:
            print("Prediction failed.")
            print(e)

        s.close()

        return prediction

if __name__ == "__main__":
    test = IRClient()
    cwd = os.getcwd()

    start = datetime.now()
    testRes = test.classify_req(cwd + "/test.jpg")
    end = datetime.now()

    print("found " + testRes)
    print("in (hh:mm:ss.ms) {}".format(end-start))

