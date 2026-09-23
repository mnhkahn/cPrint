tell application "Microsoft PowerPoint"
	tell slide 4 of active presentation
		set lock aspect ratio of picture 13 to false
		set left position of picture 13 to 205.0
		set top of picture 13 to 124.0
		set width of picture 13 to 550.0
		set height of picture 13 to 412.5
		delete shape 12
		delete shape 11
		delete shape 10
		delete shape 9
		delete shape 8
		delete shape 7
		delete shape 6
		delete shape 5
		delete shape 4
	end tell
	save active presentation
end tell
