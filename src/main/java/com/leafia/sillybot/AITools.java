package com.leafia.sillybot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class AITools {
	public static Map<String,AITool> tools = new HashMap<>();
	@FunctionalInterface
	public interface AIToolFunction {
		String call(MessageReceivedEvent evt,List<String> args) throws WrongArgumentsException, InvalidArgumentException;
	}
	public static class AITool {
		public AIToolFunction func;
		public String description;
		public AITool(AIToolFunction func,String... description) {
			this.func = func;
			StringBuilder builder = new StringBuilder();
			for (String s : description) {
				if (!builder.isEmpty()) builder.append(" | ");
				builder.append(s);
			}
			this.description = builder.toString();
		}
	}
	static {
		/*tools.put("reverseOrder",new AITool(
				(args)->{
					StringBuilder builder = new StringBuilder();
					for (String s : args.reversed()) {
						if (!builder.isEmpty()) builder.append(" ");
						builder.append(s);
					}
					return builder.toString();
				},
				//"Example tool for you.", i think this confuses the ai
				"Accepts any amount of arguments, reverses their order, and returns them in one string, separated by spaces."
		));*/ //fuck off the whole thing confuses the ai
		tools.put("getHash",new AITool(
				(evt,args)->{
					if (args.size() != 1)
						throw new WrongArgumentsException(1,args.size());
					return "\""+args.get(0)+"\" java hash is: "+String.valueOf(args.get(0).hashCode());
				},
				"Returns the java hash of text.",
				"Takes 1 argument"
		));
		tools.put("getForgetfulness",new AITool(
				(evt,args)->{
					if (AICore.memory >= 150)
						return "Mrowia is not so forgetful right now";
					else if (AICore.memory >= 50)
						return "Mrowia is sort of forgetful right now";
					return "Mrowia is very forgetful right now";
				},
				"Returns how forgetful you are currently.",
				"Takes no arguments"
		));
		tools.put("getNameFromUserId",new AITool(
				(evt,args)->{
					if (args.size() != 1)
						throw new WrongArgumentsException(1,args.size());
					if (evt == null)
						return "?";
					String s = args.getFirst();
					if (s.startsWith("<@"))
						s = s.substring(2);
					if (s.endsWith(">"))
						s = s.substring(0,s.length()-1);
					if (s.equals("1514985371711176906")) // itself
						return "Mrowia";
					Member member = evt.getGuild().getMemberById(s);
					if (member == null)
						return "?";
					return member.getEffectiveName();
				},
				"Returns user's name from long ID.",
				"For example: <@536477369238814721>, this is a user id.",
				"Takes 1 argument (must be either just numbers or <@numbers>)"
		));
	}
	public static void appendToolList(StringBuilder builder) {
		for (Entry<String,AITool> entry : tools.entrySet())
			builder.append("\n").append("\"").append(entry.getKey()).append("\": ").append(entry.getValue().description);
	}
	public static class InvalidArgumentException extends Exception {
		public InvalidArgumentException(String message) {
			super(message);
		}
	}
	public static class WrongArgumentsException extends Exception {
		public final int required;
		public final int provided;
		public WrongArgumentsException(int required,int provided) {
			this.required = required;
			this.provided = provided;
		}
	}
}
