import cv2
import numpy as numpy

def boxify(im):
    rects = list()
    
    imgray = cv2.cvtColor(im,cv2.COLOR_BGR2GRAY)
    imgrayblur = cv2.GaussianBlur(imgray, (5,5), 0)
    ret,thresh = cv2.threshold(imgray,127,255,cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    im2, contours, hierarchy = cv2.findContours(thresh,cv2.RETR_TREE,cv2.CHAIN_APPROX_SIMPLE)

    for cont in contours:
        x,y,w,h = cv2.boundingRect(countours)
        #print(x + " " + y + " " + w + " " + h + " ")
        rects.append((x, y, w, h))
        
    return rects
        
# test method
if __name__ == "__main__":
    cap = cv2.VideoCapture("")

    ret, frame = cap.read()
    boxify(frame)
