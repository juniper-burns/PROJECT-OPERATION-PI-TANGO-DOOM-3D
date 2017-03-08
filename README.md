# PROJECT-OPERATION-PI-TANGO-DOOM-3D

This is a project to be submitted to the OSISoft 2017 User's Conference PI Coresight Hackathon. This project is a proof of concept for 
determining if Google's new Augmented Reality project, Google Tango, would be useful and applicable to consumers of PI and it's systems. 
This project is a combination of an Android Application and a PI Coresight Symbol. The goals of this project are layed out in the 
following steps:

1. 2D mapping
Using Tango, we will create an application that can be used to set waypoints while navigating a facility. Tango creates a blueprint of 
the area as our application incorporates the waypoints we set into Tango’s mapping of an area being navigated. We will incorporate 
AF Elements corresponding to waypoints that can then be viewed through our plug-in made during the hackathon.  
 
2. 3D mapping
Tango gives us the ability to turn a facility into a 3D model.  Once we complete the 2D mapping, our goal is to add the ability to 
render a room and give each waypoint a location on the Z axis along with its X and Y coordinates. The spatial nature of the 3D mapping
gives the ability to differentiate waypoints in the case where multiple devices share the same coordinates but not the same physical 
space.
 
3. 3D interactive visualization
Interactive 3D model that will allow the user to move around and look at each waypoint through Coresight while sitting at their desk.
 
4. Mobile visualization in the field
Add a feature to our application where a user can use a compatible device (research pending) to access data for a specific waypoint 
while physically being in the room with that waypoint rather than having to return to a control room or their desk. This data will 
include anything associated to the AF Element connected to the waypoint.
 
5. Virtual reality
(While we may not get this far, but in the spirit of thinking big, when we do we may request VR goggles). Take the interactive 3D 
model and make it available through Virtual Reality goggles, enabling users to walk through a facility and view PI data through an 
immersive experience.

While we do not expect to reach every step by the Hackathon's completion, we do hope completeing even one of them will be 
proof-of-concept enough.

## Installation

Installing this application will require a Google Tango enabled device, of which there is only one on the market, the Lenovo Phab 2 Pro, 
for commercial use which can be found [here](http://shop.lenovo.com/us/en/tango/index.html).

You will also require Android Studio to view and build the project which can be downloaded [here](https://developer.android.com/studio/index.html).

The Google Tango Java API can be found [here](https://developers.google.com/tango/apis/java/) for any documentation you may need.

This repository also contains the Javascript code you will need to transfer over to your PI Coresight page in order to use the custom
symbol.

## Usage

Using this application simply requires you to connect your Google Tango enabled device to your machine and then run and build the 
application onto it through Android Studio. After that, the app will map out the floor plan of the building you are in by you walking
around with the camera pointed at the walls. As you scan the environment, you can add waypoints seen as red dots where your PI data
sources are located. Then, you can export the image to the Gallery on your Android device and from there upload it to the custom symbol
on PI Coresight. The symbol will search for the waypoints and automatically create an object at each point where a tag can be associated
with it in order to display all the relevant data for that data source.

## Credits

The members of Hackathon Team Doomguy are as follows:
- Phillip Little
- Johnathan Burns
- Andrew Bathon
- Simon Boka

We would also like to thank Jon Muraski for being our project aid and overseer who helped us get this project approved.

## License

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may
obtain a copy of the License [here](http://www.apache.org/licenses/LICENSE-2.0). 

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, 
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions 
and limitations under the License.
