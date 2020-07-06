# Mobile application for garbage detection
The project consists in a mobile application, written in Kotlin, and a Server created with [loopback](https://www.freecodecamp.org/news/build-restful-api-with-authentication-under-5-minutes-using-loopback-by-expressjs-no-programming-31231b8472ca/). The main idea is to be used as a game, that stimulates the garbage collecting process by detecting trash in the environment. After the user throws it in a recycle bin, he is rewarded with points for each detected litter. It is made to contains multiple users, each user can edit its profile and view a leaderboard with  all the players.

### In order to start the server use:
    $ npm install
    $ node .


### __Screenshots__

#### Authentication
![Authentication](https://user-images.githubusercontent.com/38310636/86590602-9d1bcb00-bf98-11ea-8e5b-934181b88611.png)

#### LeaderBoard
![leaderCollage](https://user-images.githubusercontent.com/38310636/86590901-2b904c80-bf99-11ea-9a71-9280a70c233f.png)

#### Detection
![cameraCollage](https://user-images.githubusercontent.com/38310636/86591129-9ccfff80-bf99-11ea-84f9-3a3022a92ff8.png)


### **Info**:
For post a new user in loopback use:
    
    {  
    "username": "ami",
    "email": "a@a.com",
    "password": "123456"
    }

If you make changes in db you should uncomment autoMigrate line call.

FindOne methods works with email field.


* The application supports tflite models
*  the server part is written using loopback
*   the server needs a mysql database connection 

*This project was part of my Bachelor Thesis*