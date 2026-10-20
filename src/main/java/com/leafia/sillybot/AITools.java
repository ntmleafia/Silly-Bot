package com.leafia.sillybot;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;

public class AITools {
	public static Map<String,AITool> tools = new HashMap<>();
	@FunctionalInterface
	public interface AIToolFunction {
		String call(MessageReceivedEvent evt,List<String> args) throws Exception;
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
					if (s.startsWith("<"))
						s = s.substring(1);
					if (s.startsWith("@"))
						s = s.substring(1);
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
				"Use when there's <@NUMBER>.",
				"Takes 1 argument (must be either just numbers or <@numbers>)"
		));
		tools.put("searchInternet",new AITool(
				(evt,args)->{
					if (args.size() != 1)
						throw new WrongArgumentsException(1,args.size());
					String query = URLEncoder.encode(args.getFirst(),StandardCharsets.UTF_8);
					String url = "https://html.duckduckgo.com/html/?q="+query;
					HttpClient client = HttpClient.newHttpClient();
					HttpRequest request = HttpRequest.newBuilder()
							.uri(URI.create(url))
							.header("User-Agent", "Mozilla/5.0")
							.GET()
							.build();
					HttpResponse<String> response = client.send(
							request,
							HttpResponse.BodyHandlers.ofString()
					);
					Document doc = Jsoup.parse(response.body());
					StringBuilder results = new StringBuilder();
					for (Element result : doc.select(".result")) {
						Element title = result.selectFirst(".result__title");
						Element description = result.selectFirst(".result__snippet");
						if (title != null && description != null) {
							if (!results.isEmpty())
								results.append("\n"); //                     V that's what you get busta :3 :3 mrow
							results.append(title.text()).append(": ").append(addMeowing(description.text()));
						}
					}
					return results.toString();
				},
				"Returns bunch of search results from the internet.",
				"Only use it when you are asked about information you don't know.",
				"Comes with bunch of random meowing. Say them, btw.",
				"Takes 1 argument"
		));
	}
	public static final Random rand = new Random();
	public static final String[] meowing = new String[]{"meow", "mrow~", "nya", "nyawr", "nrrrr"};
	public static String addMeowing(String original) {
		StringBuilder builder = new StringBuilder();
		for (String s : original.split(" ")) {
			if (!builder.isEmpty())
				builder.append(" ");
			if (rand.nextInt(4) == 0)
				builder.append(meowing[rand.nextInt(meowing.length)]).append(" ");
			builder.append(s);
		}
		return builder.toString();
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
