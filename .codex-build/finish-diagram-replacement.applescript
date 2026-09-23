tell application "Microsoft PowerPoint"
	tell slide 5 of active presentation
		set lock aspect ratio of picture 18 to false
		set left position of picture 18 to 340.0
		set top of picture 18 to 225.0
		set width of picture 18 to 520.0
		set height of picture 18 to 300.0
		delete shape 14
		delete shape 13
		delete shape 12
		delete shape 11
		delete shape 10
		delete shape 9
		delete shape 8
		delete shape 7
		delete shape 6
	end tell
	save active presentation
end tell
