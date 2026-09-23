set videoPath to "/Users/mnhkahn/Downloads/share_3d9a2a46f385ada8cbbc08158279e9ae1789816309711.mp4"

with timeout of 120 seconds
	tell application "Microsoft PowerPoint"
		tell slide 7 of active presentation
			set content of text range of text frame of shape 1 to "06 / 08"
			set content of text range of text frame of shape 2 to "小行星被黑洞吞噬（视频演示）"
			set content of text range of text frame of shape 4 to "一段视频看完整过程"
			set content of text range of text frame of shape 5 to "1. 离得远：小行星能稳定绕行。\r2. 越来越近：引力拉弯轨道。\r3. 太靠近：被拉伸，越过事件视界后无法返回。"
			delete shape 13
			delete shape 12
			delete shape 11
			delete shape 10
			delete shape 9
			delete shape 8
			delete shape 7
			delete shape 6
			set embeddedMovie to make new media2 object at end with properties {file name:videoPath, lock aspect ratio:true, left position:525.0, top:115.0, width:225.0, height:400.0}
			set play on entry of play settings of animation settings of embeddedMovie to false
		end tell
		delete slide 9 of active presentation
		delete slide 8 of active presentation
		tell slide 2 of active presentation to set content of text range of text frame of shape 1 to "01 / 08"
		tell slide 3 of active presentation to set content of text range of text frame of shape 1 to "02 / 08"
		tell slide 4 of active presentation to set content of text range of text frame of shape 1 to "03 / 08"
		tell slide 5 of active presentation to set content of text range of text frame of shape 1 to "04 / 08"
		tell slide 6 of active presentation to set content of text range of text frame of shape 1 to "05 / 08"
		tell slide 7 of active presentation to set content of text range of text frame of shape 1 to "06 / 08"
		tell slide 8 of active presentation to set content of text range of text frame of shape 1 to "07 / 08"
		save active presentation
	end tell
end timeout
