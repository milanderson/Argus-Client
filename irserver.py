# A server for handling image classification and training
# requests. Runs as a daemon, listening for incoming requests
# and dispatching them to threads for training or classification.

import os, sys, time, socket, threading
from datetime import datetime
from daemon import Daemon

REFUSE_IR_REQ = "Request not recognized. Valid requests are Train|Classify"
IR_REQ_TR = 'Train'
IR_REQ_CL = 'Classify'
IR_READY = "Ready"
CURR_FILE_ID = 0

class IRDispatcher(Daemon):

    def run(self):
        self.init_ir_server()

    def next_filename(self):
        now = datetime.now()
        folderPath = "/" + str(now.year) + "_" + str(now.month) + "_" + str(now.day)

        if not os.path.isdir(folderPath):
            os.makedirs(folderPath, mode=0777)

            self.fileID = 0

        self.fileID += 1
        fileName = str(self.fileID).zfill(19) + ".jpg"
        return folderPath + "/" + fileName

    def init_ir_server(self):
        self.fileID = 0
        HOST = ''
        PORT = 50505

        s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        s.bind(('localhost', 50505))
        s.listen(3)

        while True:
            conn, addr = s.accept()
            data = conn.recv(1024)

            if IR_REQ_TR in data:
                path = self.next_filename()
                t = threading.Thread(target=train_thread, args=(conn, path))
                t.start()
            elif IR_REQ_CL in data:
                path = self.next_filename()
                t = threading.Thread(target=classify_thread, args=(conn, path))
                t.start()
            else:
                conn.sendall(REFUSE_IR_REQ)
                conn.close()

# recieve and process an image training request
def classify_thread(conn, filepath):
    f = open(filepath, 'w+')

    conn.sendall(IR_READY)
    while True:
        data = conn.recv(1024)
        if not data or "Done" in data:
            break
        f.write(data)
    f.close()

    prediction = relay_train_req(filepath)
    conn.sendall(prediction)

    conn.close()


# recieve and process an image classification request
def train_thread(conn, filepath):
    f = open(filepath, 'w+')

    conn.sendall(IR_READY)
    while True:
        data = conn.recv(1024)
        if not data or "Done" in data:
            break
        f.write(data)
    conn.close()

def relay_train_req(filepath):
    # direct this to any classifier
    printout = os.popen("python /home/student/Desktop/tutorial-2-image-classifier/predict.py " + filepath).read()
    answer = printout[printout.find("[[") + 2:len(printout) - 3].split()

    if float(answer[0]) > float(answer[1]):
        prediction = "dog"
    else:
        prediction = "cat"
    return prediction


# init daemon
if __name__ == "__main__":
    daemon = IRDispatcher('/tmp/IRDispatcher.pid')
    if len(sys.argv) == 2:
        if 'start' == sys.argv[1]:
                daemon.start()
        elif 'stop' == sys.argv[1]:
                daemon.stop()
        elif 'restart' == sys.argv[1]:
                daemon.restart()
        else:
                print "Unknown command"
                sys.exit(2)
        sys.exit(0)
    else:
        print "usage: %s start|stop|restart" % sys.argv[0]