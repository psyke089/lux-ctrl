create an android app to control hue lightbulbs

# techincal
- android studio etc is installed
- initialize new private codeberg repo with this dir aas project root
- create a new android/kotlin project, API version 24 (if that makes sense, pls prompt otherwise), in this dir
- avoid useing unecessary dependancies
- the app should work offline (meaning no internet, it obviously needs to acces local network)
- you can find technical infos (bridge ip / key) in ./tmp/hue.md.
- there is a phone connected via usb debugging which you can use for testing
- add a README.md with concise explenation of the app, add a screenshot, and git badges. you can use ~/projects/enso/README.md as guidance

# features:
- settings panel to set bridge ip and key
- direct lamp control
- scene picker
- create / update / delete scene

# visual design / layout

- only single (dark) design
- use the mockup in ./tmp/mock.png as guidance for the design language/vibe
- generate all needed assets / icons yourself, no downloading stuff from the net
- two section layout
- top: individual lamp control
- bottom: scene picker
- if phone is in landscape mode individual lamps go right, scenes left

## top half of screen: individual lamp control
    - there is a mockup in ./tmp/mock.png
    - each lamp is represented by a slice of the circle
    - swiping a slice outward from circle center should increase brightness
    - swiping a slice inward, towards the circle cenhter should decrease brightness
    - tapping a slice should open a color picker to change the color
    - center should be a on/off switch that toggles ALL lights
    - center should have an icon representing the function. drop the "05/06 reported" text currently in the mockup

## bottom half of screen: scene selector
    - button for each scene
    - each button should reflect the colors the scene is made up off
    - tapping activates scene
    - hold button opens dialog for:
        - delete
        - rename
    - save button:
        - if a scene is active, overwrite it
        - if no scene is active, ask for name to save as

Any open questions: Ask me!
