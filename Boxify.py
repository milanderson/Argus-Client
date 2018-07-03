import cv2
import numpy as np

def boxify(im):
    imgray = cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
    ret,thresh = cv2.threshold(imgray,127,255,0)
    im2, contours, hierarchy = cv2.findContours(thresh,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)
    print(countours)

    x,y,w,h = cv2.boundingRect(countours)
    print(x + " " + y + " " + w + " " + h + " ")

# test method
if __name__ == "__main__":
    cap = cv2.VideoCapture("")

    ret, frame = cap.read()
    boxify(frame)