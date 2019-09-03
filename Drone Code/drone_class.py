from dronekit import connect, VehicleMode, LocationGlobal, LocationGlobalRelative
from pymavlink import mavutil # Needed for command message definitions
# from openmv_pi import openMV
import time
import math


class DroneControl(object):
    def __init__(self):
        #Dronekit Vehicle Object
        self.vehicle = connect('/dev/ttyUSB0', baud=115200, wait_ready=True)
        
        #Default to no GPS
        self.has_gps = False
        
        
        
        
    #====================PRIVATE FUNCTIONS===============================================    
        
        
    #Function Emulates a Controller to Command the Drone
    #Vehicle Must be in a mode that accepts controller overrides
    def __override_controller(self, throttle, roll, pitch, yaw):
        """
        Overrides Controller Input Channels Where:
        1. Roll, inverted, [-500, 500]
        2. Pitch, [-500,500]
        3. Throttle, [0,1000]
        4. Yaw, [-500,500]
        """
        
        self.vehicle.channels.overrides = {'1': 1500+roll, '2': 1500+pitch, '3': 1000+throttle, '4': 1500-yaw}
        
    #Remove Controller Inputs, Be careful, could cause drone to fall
    def __remove_controller_override(self):
        self.vehicle.channels.overrides = {}
            
    
    #Command drone using roll and pitch angles. Be wary of this function because thrust depends on barometer, so height will never be accurate
    def __send_attitude_target(roll_angle = 0.0, pitch_angle = 0.0,
                         yaw_angle = None, yaw_rate = 0.0, use_yaw_rate = False,
                         thrust = 0.5):
        """
        use_yaw_rate: the yaw can be controlled using yaw_angle OR yaw_rate.
                      When one is used, the other is ignored by Ardupilot.
        thrust: 0 <= thrust <= 1, as a fraction of maximum vertical thrust.
                Note that as of Copter 3.5, thrust = 0.5 triggers a special case in
                the code for maintaining current altitude.
        """
        if yaw_angle is None:
            # this value may be unused by the vehicle, depending on use_yaw_rate
            yaw_angle = self.vehicle.attitude.yaw
            roll_angle = self.vehicle.attitude.roll + roll_angle
            pitch_angle = self.vehicle.attitude.pitch + pitch_angle
        # Thrust >  0.5: Ascend
        # Thrust == 0.5: Hold the altitude
        # Thrust <  0.5: Descend
        msg = self.vehicle.message_factory.set_attitude_target_encode(
            0, # time_boot_ms
            1, # Target system
            1, # Target component
            0b00000000 if use_yaw_rate else 0b00000100,
            to_quaternion(roll_angle, pitch_angle, yaw_angle), # Quaternion
            0, # Body roll rate in radian
            0, # Body pitch rate in radian
            math.radians(yaw_rate), # Body yaw rate in radian/second
            thrust  # Thrust
        )
        self.vehicle.send_mavlink(msg)
        
    
    
    def __set_attitude(roll_angle = 0.0, pitch_angle = 0.0,
                 yaw_angle = None, yaw_rate = 0.0, use_yaw_rate = False,
                 thrust = 0.5, duration = 0):

        self.__send_attitude_target(roll_angle, pitch_angle,
                             yaw_angle, yaw_rate, False,
                             thrust)
        start = time.time()
        while time.time() - start < duration:
            send_attitude_target(roll_angle, pitch_angle,
                                 yaw_angle, yaw_rate, False,
                                 thrust)
            time.sleep(0.1)
        # Reset attitude, or it will persist for 1s more due to the timeout
        send_attitude_target(0, 0,
                             0, 0, True,
                             thrust)
        
        
        
    """
    Vehicle Must Be In Guided Mode
    Move vehicle in direction based on specified velocity vectors
    Message Must be received every 3 seconds or the vehicle will stop moving
    velocity_x: North Velocity (m/s)
    velocity_y: East Velocity (m/s)
    velocity_z: Down velocity, positive toward ground (m/s)
    """
    def __send_velocity_global_frame(self, velocity_x, velocity_y, velocity_z):
        msg = self.vehicle.message_factory.set_position_target_local_ned_encode(
            0,       # time_boot_ms (not used)
            0, 0,    # target system, target component
            mavutil.mavlink.MAV_FRAME_LOCAL_NED, # frame
            0b0000111111000111, # type_mask (only speeds enabled)
            0, 0, 0, # x, y, z positions (not used)
            velocity_x, velocity_y, velocity_z, # x, y, z velocity in m/s
            0, 0, 0, # x, y, z acceleration (not supported yet, ignored in GCS_Mavlink)
            0, 0)    # yaw, yaw_rate (not supported yet, ignored in GCS_Mavlink)
        
        # send command to vehicle
        self.vehicle.send_mavlink(msg)
        
        
    """
    Vehicle Must Be In Guided Mode
    Move vehicle in direction relative to vehicle frame
    Message Must be received every 3 seconds or the vehicle will stop moving
    velocity_x: Forward Velocity (m/s)
    velocity_y: Horizontal Velocity (m/s)
    velocity_z: Down velocity, positive toward ground (m/s)
    """
    def __send_velocity_vehicle_frame(self, velocity_x, velocity_y, velocity_z):
        msg = self.vehicle.message_factory.set_position_target_local_ned_encode(
            0,       # time_boot_ms (not used)
            0, 0,    # target system, target component
            mavutil.mavlink.MAV_FRAME_BODY_OFFSET_NED, # frame
            0b0000111111000111, # type_mask (only speeds enabled)
            0, 0, 0, # x, y, z positions (not used)
            velocity_x, velocity_y, velocity_z, # x, y, z velocity in m/s
            0, 0, 0, # x, y, z acceleration (not supported yet, ignored in GCS_Mavlink)
            0, 0)    # yaw, yaw_rate (not supported yet, ignored in GCS_Mavlink)
        
        # send command to vehicle
        self.vehicle.send_mavlink(msg)
        
        
        
        
    #=======================PUBLIC FUNCTIONS============================================    
        
        
    #Completely Stop Drone for 30s (this may not work in waypoint navigation missions)
    def emergency_stop(self):
        self.vehicle.mode = VehicleMode("STABILIZED")
        t = time.time()
        while time.time() < (t + 30):
            self.__override_controller(0, 0, 0, 0);
            
    def update_parameters(self):
        self.parameters = (self.vehicle.gps_0, self.vehicle.battery, self.vehicle.is_armable, self.vehicle.system_status.state, self.vehicle.mode.name)
    
    
    #note, altitude is relative to the HOME location of the drone
    def waypoint_navigation(self, lat, longitude, alt = 4, speed = 1):
        location = LocationGlobalRelative(lat,longitude,alt)
        self.vehicle.simple_goto(location, groundspeed=speed)
        
    #Put drone in land mode
    def land_vehicle(self):
        self.vehicle.mode = VehicleMode("LAND")
    
    #Takeoff, nogps
    def arm_and_takeoff_nogps(self, aTargetAltitude):
        #Arms vehicle and fly to aTargetAltitude without GPS data.

        ##### CONSTANTS #####
        DEFAULT_TAKEOFF_THRUST = 0.7
        SMOOTH_TAKEOFF_THRUST = 0.6

        print("Arming motors")
        # Copter should arm in GUIDED_NOGPS mode
        self.vehicle.mode = VehicleMode("GUIDED_NOGPS")
        self.vehicle.armed = True

        while not self.vehicle.armed:
            print(" Waiting for arming...")
            self.vehicle.armed = True
            time.sleep(1)

        print("Taking off!")

        thrust = DEFAULT_TAKEOFF_THRUST
        while True:
            current_altitude = self.vehicle.location.global_relative_frame.alt
            print(" Altitude: %f  Desired: %f" %
                  (current_altitude, aTargetAltitude))
            if current_altitude >= aTargetAltitude*0.95: # Trigger just below target alt.
                print("Reached target altitude")
                break
            elif current_altitude >= aTargetAltitude*0.6:
                thrust = SMOOTH_TAKEOFF_THRUST
            self.__set_attitude(thrust = thrust)
            time.sleep(0.2)  
        
        self.vehicle.mode = VehicleMode("ALT_HOLD")
        
        
    #Takeoff, with gps
    def arm_and_takeoff(aTargetAltitude):
        """
        Arms vehicle and fly to aTargetAltitude.
        """

        print "Arming motors"
        # Copter should arm in GUIDED mode
        self.vehicle.mode    = VehicleMode("GUIDED")
        self.vehicle.armed   = True

        # Confirm vehicle armed before attempting to take off
        while not self.vehicle.armed:
            print " Waiting for arming..."
            time.sleep(1)

        print "Taking off!"
        self.vehicle.simple_takeoff(aTargetAltitude) # Take off to target altitude

        # Wait until the vehicle reaches a safe height before processing the goto (otherwise the command
        #  after Vehicle.simple_takeoff will execute immediately).
        while True:
            print " Altitude: ", self.vehicle.location.global_relative_frame.alt
            #Break and return from function just below target altitude.
            if self.vehicle.location.global_relative_frame.alt>=aTargetAltitude*0.95:
                print "Reached target altitude"
                break
            time.sleep(1)
        
        #self.vehicle.mode = VehicleMode("LOITER")
    
    #For Now, limiting total inputs slightly to prevent crashes
    #Special Mode for if duration = 0, stop all mode changes to allow rapid sending of commands
    def move_drone_nogps(self, thrust, roll, pitch, yaw, duration, drone_frame, phone_compass):        
        #Safety Checks
        # if ((thrust < 0 or thrust > 1000)):
        #     return
        # if abs(roll) > 300:
        #     return
        # if abs(pitch) > 300:
        #     return
        # if abs(yaw) > 300:
        #     return        

        #More robust command structure if duration is longer than a second
        if duration > 0:
            self.vehicle.mode = VehicleMode("STABILIZE")

        #Move relative to phone orientation, rather than drone orientation
        if not drone_frame:
            print("you shouldn't be here")
            angle = (phone_compass - int(self.vehicle.heading))%360
            angle = angle*math.pi/180
            newRoll = roll*math.cos(angle) - pitch*math.sin(angle)
            newPitch = roll*math.sin(angle) + pitch*math.cos(angle)
            roll = newRoll
            pitch = newPitch
         
        self.__override_controller(thrust,roll,pitch,yaw)
        
        if duration > 0:
            t = time.time()
            while time.time() < t + duration:
                self.__override_controller(thrust,roll,pitch,yaw)
                time.sleep(0.1)
            self.vehicle.mode = VehicleMode("ALT_HOLD")


    #Send velocity commands to drone
    def move_drone(self, forward_v, horizontal_v, vertical_v, drone_heading=True, duration=0):        
        #Safety Checks
        if abs(forward_v) > 10:
            return
        if abs(horizontal_v) > 10:
            return
        if abs(vertical_v) > 10:
            return        
        
        if duration > 0:
            self.vehicle.mode = VehicleMode("GUIDED")
        
        if drone_heading:
            self.__send_velocity_vehicle_frame(forward_v,horizontal_v,vertical_v)
        else:
            self.__send_velocity_global_frame(forward_v,horizontal_v,vertical_v)
        
        if duration > 0:
            t = time.time()
            while time.time() < t + duration:
                if drone_heading:
                    self.__send_velocity_vehicle_frame(forward_v,horizontal_v,vertical_v)
                else:
                    self.__send_velocity_global_frame(forward_v,horizontal_v,vertical_v)
                time.sleep(0.1)
            self.vehicle.mode = VehicleMode("LOITER")

    #Heading from 0-360 from north, or if relative is true then from current vehicle heading
    def set_heading(self, heading, relative=False):
        if relative:
            is_relative=1 #yaw relative to direction of travel
        else:
            is_relative=0 #yaw is an absolute angle
        # create the CONDITION_YAW command using command_long_encode()
        msg = vehicle.message_factory.command_long_encode(
            0, 0,    # target system, target component
            mavutil.mavlink.MAV_CMD_CONDITION_YAW, #command
            0, #confirmation
            heading,    # param 1, yaw in degrees
            0,          # param 2, yaw speed deg/s
            1,          # param 3, direction -1 ccw, 1 cw
            is_relative, # param 4, relative offset 1, absolute angle 0
            0, 0, 0)    # param 5 ~ 7 not used
        # send command to vehicle
        vehicle.send_mavlink(msg)

    #Arm drone
    def arm_drone(self, armed):
        if armed:
            self.vehicle.armed = True
        else:
            self.vehicle.armed = False
        if armed:
            print("Arming Drone")
            t = time.time()
            while (not self.vehicle.armed) and (time.time() < t + 10):
                print("waiting for arming")
                self.vehcile.armed = True
                time.sleep(1)
            if not self.vehicle.armed:
                print("Failed to arm drone")
        else:
            print("Disarming Drone")
        t = time.time()

    #Create status message to send to android application
    def status(self):
        msg = "%s;%s;%s;%s;%s;%s;%s\n" %(self.vehicle.battery,self.vehicle.mode.name, self.vehicle.gps_0,self.vehicle.location.global_relative_frame,self.vehicle.armed,self.vehicle.is_armable,self.vehicle.version)
        return msg 
    def land(self):
        self.vehicle.mode = VehicleMode("LAND")

