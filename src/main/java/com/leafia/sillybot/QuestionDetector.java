package com.leafia.sillybot;

public class QuestionDetector {
	public static boolean howTo(String s) {
		if (SillyBot.containsRegex(s,"how")) {
			if (SillyBot.containsAllRegexes(s,"to") || SillyBot.containsAllRegexes(s,"do","i"))
				return true;
		}
		if (SillyBot.containsRegex(s,"how")) {
			if (SillyBot.containsAllRegexes(s,"to") || SillyBot.containsAllRegexes(s,"do","i") || SillyBot.containsAllRegexes(s,"do","you") || SillyBot.containsAllRegexes(s,"do","u"))
				return true;
		}
		return false;
	}
	public static boolean anyWayTo(String s) {
		if (SillyBot.containsAllRegexes(s,"any","way","to") || SillyBot.containsAllRegexes(s,"is","possible","to"))
			return true;
		return false;
	}
}
