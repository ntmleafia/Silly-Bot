package com.leafia.sillybot;

public class QuestionDetector {
	public static boolean howTo(String s) {
		if (SillyBot.containsRegex(s,"how")) {
			if (SillyBot.containsRegex(s,"to") || SillyBot.containsRegexes(s,"do","i"))
				return true;
		}
		if (SillyBot.containsRegex(s,"how")) {
			if (SillyBot.containsRegex(s,"to") || SillyBot.containsRegexes(s,"do","i") || SillyBot.containsRegexes(s,"do","you") || SillyBot.containsRegexes(s,"do","u"))
				return true;
		}
		return false;
	}
	public static boolean anyWayTo(String s) {
		if (SillyBot.containsRegexes(s,"any","way","to"))
			return true;
		return false;
	}
}
