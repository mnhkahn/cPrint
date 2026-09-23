with timeout of 120 seconds
	tell application "Microsoft PowerPoint"
		set theSlide to slide 5 of active presentation
		tell theSlide
			-- Make the embedded vertical video the main visual on the left.
			repeat with shapeIndex in {20, 21}
				set lock aspect ratio of shape shapeIndex to false
				set left position of shape shapeIndex to 45.0
				set top of shape shapeIndex to 130.0
				set width of shape shapeIndex to 195.0
				set height of shape shapeIndex to 338.0
			end repeat

			-- Move the explanation to the upper-right, above the lensing diagram.
			set content of text range of text frame of shape 4 to "吸积盘本身是一个扁平的圆盘"
			set left position of shape 4 to 375.0
			set top of shape 4 to 128.0
			set width of shape 4 to 380.0
			set content of text range of text frame of shape 5 to "黑洞会弯曲光线，让圆盘远侧的光绕到我们眼前，像在黑洞上方和下方又出现一圈。"
			set left position of shape 5 to 375.0
			set top of shape 5 to 160.0
			set width of shape 5 to 390.0
			set height of shape 5 to 60.0

			-- Move the upper lensed image down so it does not collide with the new text.
			set top of shape 11 to 228.0
			set top of shape 12 to 238.0

			-- Reuse the caption as the playback cue, then remove the old screenshot and file-name line.
			set content of text range of text frame of shape 16 to "点击播放中文配音视频"
			set left position of shape 16 to 45.0
			set top of shape 16 to 480.0
			set width of shape 16 to 195.0
			delete shape 19
			delete shape 15
		end tell
		save active presentation
	end tell
end timeout
