set imagePath to "/Users/mnhkahn/code/cPrint/output/吸积盘引力透镜示意图-中文.png"
set imageFile to POSIX file imagePath as alias

tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		delete shape 14
		delete shape 13
		delete shape 12
		delete shape 11
		delete shape 10
		delete shape 9
		delete shape 8
		delete shape 7
		delete shape 6
		make new picture at end with properties {file name:imageFile, link to file:false, save with document:true, lock aspect ratio:true, left position:340.0, top:225.0, width:533.0, height:300.0}
	end tell
	save active presentation
end tell
