from BaseHTTPServer import HTTPServer, BaseHTTPRequestHandler
import numpy as np
import cv2

class RequestHandler(BaseHTTPRequestHandler):

    def do_GET(self):
        print("get recieved")

    def do_POST(self):
        print("post recieved")
        self.rfile._sock.settimeout(20)

        content_length = self.headers.getheaders('content-length')
        data = self.rfile.read(int(content_length[0]))
        readLength = len(data)
        print("read", readLength, "bytes", str(data[0:4]))

        recvNum = str(data[0:4])

        if int(recvNum) % 2  == 1:
            h = 240
            w = 320
        else:
            h = 480
            w = 640

        #data = data[5:int(content_length[0])]
        #frame = np.frombuffer(data, dtype=np.uint8)
        #frame = np.reshape(frame, ((h*3)/2, w))
        #RGBMatrix = cv2.cvtColor(frame, cv2.COLOR_YUV2BGR_YV12)

        lineA = "0000011000001100000"
        lineB = "0000000000000000000"
        lineC = "0000011111111100000"
        lineD = "0000000011100000000"

        maskStr = ""
        # UNIT TEST 2
        for i in range(3):
            maskStr += lineB
        for i in range(3):
            maskStr += lineA
        for i in range(10):
            maskStr += lineC
        for i in range(3):
            maskStr += lineB

        fullStr = '[{ "class": "Dog", "confidence": 0.7284756, "bounding_box": [10,20,260,280], "bit_mask": "' + maskStr + '" }]'

        self.send_response(200)
        self.send_header('Content-type', 'text/html')
        self.end_headers()
        self.wfile.write(str(data[0:4]) + fullStr)

        """ while readLength < data:
            data += self.rfile.read(content_length)
            readLength += len(data)

            print(readLength)

            if(data >= 460800):
                frame = data[0:460800]
                data = data[460800:]

                frame = np.frombuffer(frame, dtype=np.uint8)
                frame = np.reshape(frame, (h*3/2, w))

                RGBMatrix = cv2.cvtColor(frame, cv2.COLOR_YUV2BGR_NV21)

                cv2.imshow('frame', RGBMatrix)
                if cv2.waitKey(1) & 0xFF == ord('q'):
                    break """

def run(port=80):
    server_address = ('', port)
    server = HTTPServer(server_address, RequestHandler)
    print('Starting httpd...')
    server.serve_forever()

if __name__ == "__main__":
    run(port=50505)