pkill -9 -f "python"
fuser -k 5005/tcp
python video_server.py &
python android_app_driver.py &
