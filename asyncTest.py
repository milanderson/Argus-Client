import asyncio
import time
import random

@asyncio.coroutine
def testFun():
    yield From(asyncio.sleep(3))

def main():
    loop = asyncio.get_event_loop()
    tasks = [asyncio.async(testFun())]
    loop.run_until_complete(asyncio.wait(tasks))
    while true:
        print("loop top")
        time.sleep(1)
    

if __name__ == "__main__":
    main()