# A) global
1. change title to "LUX//CTRL"
3. refresh seems to trigger a small but noticable flicker of some ui elements - can we het rid of that ?

# B) settings
1. change headline to "SETTINGS"
2. remove the text explanations below headline and at the bottom. Headline and input labels suffice
3. add option to set update interval

# C) light field
1. remove redundant buttons below wheel
2. add names to wheel labels
3. add some indication which scene is active
4. remove "DRAG TO DIM" / "TAP TO COLOR" texts
5. make wheel as big as possible, without clipping the outer labels
6. animate the change when picking a different scene
7. remove lamp count ("06 LAMPS")

# D) lamp dialog
1. remove "TURN OFF" button
2. remove "changes apply..." text
3. remove cancel/apply buttons
4. apply the change directly after the color disc was used
5. add a 'x' to close, top right corner

# E) scenes
1. remove "Set the Atomsphere" text
2. remove giant save button at the bottom
3. Make the color indicator on the scene buttons more prominent
4. remove light count on the scene buttons
5. lets try a 3x4 grid for the buttons
6. change "+ New" button to just "Save", behaviour:
    - open dialog with name field
    - if a scene is active: field is populated with current scene name, 'save' overwrites it
    - if no scene is active: name field is empty, 'save' creates a new scene, name field it required
