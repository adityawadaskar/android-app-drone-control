import socket
import threading
from drone_class import DroneControl
import string

# Use lock.acquire() when you are entering a part of the code that makes modifications to a shared object to
# ensure that no other threads modify that object during this time.
# Don't forget to use lock.release() [!] when you are done modifying the object, or the program will get stuck here.
lock = threading.Lock()
IP_ADDRESS = "192.168.11.115"
PORT = 5005
client_conn = None
# Use threading.Events to interrupt other threads/check if a condition is still true before moving on.
# http://zulko.github.io/blog/2013/09/19/a-basic-example-of-threads-synchronization-in-python/
# Basically, create an event for things that might happen such a "new command event"
# Clear the event when it is not true, set the event when it becomes true, and check if the event is set or not
# before doing something that is dependent on the event happening.


def connect_to_app():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind((IP_ADDRESS, PORT))
    print('[STATUS] Drone is bound to ' + IP_ADDRESS + ", " + str(PORT) + " and waiting for app connection.")
    sock.listen(1)
    conn, addr = sock.accept()
    print("Successfully connected to " + str(addr))
    return conn


def connect_to_drone():
    print('[STATUS] Pi is Connecting to Pixracer')
    drone = DroneControl()
    print('Successfully connected to Pixracer')
    return drone


class AndroidAppDriver:
    def __init__(self, drone, app_stream):
        self.drone = drone
        self.app_stream = app_stream

    #Waypoint commands are commented out because compass on drone is damaged, will cause drone to crash
    def listen_for_user_input(self):
        def listen_thread():
            while True:
                try:
                    incoming_command = android_app_stream.recv(1024)
                    words = string.split(incoming_command,",")
                    print("[STATUS] Processing string: " + incoming_command)
                    if "quit" in incoming_command:
                        print("Quit Command Read")
                    if "move" in incoming_command:
                        print("Move command Read")
                        self.drone.move_drone_nogps(float(words[3]), float(words[1]), float(words[2]), float(words[4]), 0, 1, 0)
                    if "takeoff" in incoming_command:
                        print("Takeoff command read")
                        #drone.arm_and_takeoff(float(words[1]))
                    if "waypoint" in incoming_command:
                        print("Waypoint command read")
                        #drone.waypoint_navigation(float(words[1]), float(words[2]), float(words[3]),0.5)
                    if "land" in incoming_command:
                        print("Land command read")
                        #drone.land()
                    if "stop" in incoming_command:
                        drone.emergency_stop()
                    if "arm" in incoming_command:
                        drone.arm_drone(int(words[1]))
                    if "status" in incoming_command:
                        print("Status command read")
                        self.app_stream.sendall(drone.status())
                except:
                    print("Error in input command")

        curr_thread = threading.Thread(target=listen_thread, args=())
        curr_thread.start()


if __name__ == '__main__':
    drone = connect_to_drone()
    android_app_stream = connect_to_app()


    driver = AndroidAppDriver(drone, android_app_stream)

    driver.listen_for_user_input()

