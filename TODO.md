# HereWorldy
a plugin to create and manage new worlds, setup portals, boxes between "dimensions" and game modes. 

The Operators on the server will be allowed to create new worlds and setup portals to these new worlds. 
The players on the server will be allowed to "cross" to the otherworlds by standing in the portals (similar to the nether portals).

Portals will be setup by the operator by selecting a 2d area from point A to point B of a frame built vertically with a min dimension 2x3 and a max dimension 5x10. A similarly sized portal will be created on the target world by specifying a x y z location (prompted in chat). The portal frame will be built by teh same blocks as the one existing in the origin world. 
The portal if active will be highlighted with a particle effect or a portal effect if possible (preferable).

When creating worlds, the operators will have the choice to set the difficulty and the default game mode (survival, creative, hardcore). 
- travelling between survival world should try carry over the inventory and equipment
- creative and hardcore worlds should have their own player inventory

Players will be allowed to setup Inter-dimensional boxes by applying a sign to a box and writing [Here My Stuff!] on a box. This will work like an inter dimensional nether box where the player finds the items they add or be able to take items to other worlds, if the worlds allow it (i.e. can an old world receive items it didnt generate when it was created? if not, do not show them in the box at all but keep the slot occupied with an untouchable red glass panel.) 

NOTE: I would like this plugin to be able to handle creating portals to other worlds but also be able to create a new world when the new version of minecraft comes out with new biomes etc.. without destroying the current world.