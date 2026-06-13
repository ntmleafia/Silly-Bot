package com.leafia.sillybot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.Attachment;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.SelfUser;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.concrete.ForumChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.FileUpload;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.internal.entities.channel.concrete.ThreadChannelImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SillyBot {
	private static final boolean debug = true;
	private static final Logger logger = LoggerFactory.getLogger(SillyBot.class);
	private static JDA jda;
	private static final File tokenDir = new File("D:\\sillybot_token.txt"); // top secret!!
	private static SelfUser self;
	public enum ServerType {
		CURSED,WARFACTORY,DEVELOPMENT,UNKNOWN
	}
	private static long lastCommandUse = 0;
	// this is so crappy
	public static ServerType getServerType(Guild server) {
		String name = server.getName();
		if (name.equalsIgnoreCase("silly bot development"))
			return ServerType.DEVELOPMENT;
		if (!debug) {
			if (name.equalsIgnoreCase("warfactory official"))
				return ServerType.WARFACTORY;
		}
		return ServerType.UNKNOWN;
	}
	public static class Responses {
		public static Map<String,Supplier<MessageCreateBuilder>> tags = new HashMap<>();
		static {
			tags.put("qna_converter",Responses::qnaConverter);
		}
		public static MessageCreateBuilder qnaConverter() {
			return new MessageCreateBuilder()
					.setContent(
							"HE/RF converters are purely decorative in NTM:CE. Cables do not connect (see picture 1), " +
									"nor the block has any functionality at all besides spitting a wall of text on your screen." +
									"\nInstead, NTM:CE does HE/RF conversion automatically. This can be done in 2 ways:" +
									"\n\n## Connecting RF cables to HE-based machines directly" +
									"\nTo get it working, you have to connect cables that use RF directly from your HE-based machines " +
									"(NTM cables does NOT connect to RF cables, as I repeat, you have to connect the cable to machines directly)." +
									"\n\nSecondly, you have to make sure the RF-based cable you're using explicitly extracts energy from connected devices." +
									"\nFor example with Mekanism, by just connecting the cable to the battery, it doesn't work. (see picture 2)" +
									"\nFor this to work, the cable must be set to Pull connection type. (see picture 3)" +
									"\n\nI don't know much about other mods, but for example with cables from Thermal series you would need a servo, and so on." +
									"\nIf the mod has no feature to make the cable explicitly pull energy from connected devices, well, you're doomed." +
									"\n*Applied Energistics 2 uses it's own power system by the way, so this does not work. you have to store it in RF storage from other mods first*" +
									"\n**Oh and don't forget to enable output on your battery socket. I'd cry if you forget to do that while reaching this far.**" +
									"\n\n## Automatic cable conversion" +
									"\nAlternatively, you can enable autoCableConversion in hbm.cfg to make cables from NTM also transfer RF. (see picture 4)" +
									"\nYou can find this file under config/hbm folder in your Minecraft installation folder."
					)
					.addFiles(
							getUpload("qna/herf/converter.png"),
							getUpload("qna/herf/energyinvalid.png"),
							getUpload("qna/herf/energyworking.png"),
							getUpload("qna/herf/config.png")
					);
		}
	}
	public static class SillyListener extends ListenerAdapter {
		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if (event.getAuthor().equals(self)) return;
			ServerType type = getServerType(event.getGuild());
			if (!type.equals(ServerType.DEVELOPMENT) && debug) return;

			MessageChannelUnion chan = event.getChannel();
			if (type.equals(ServerType.DEVELOPMENT) && chan.getName().equalsIgnoreCase("resources"))
				return;
			Message data = event.getMessage();
			if (type.equals(ServerType.DEVELOPMENT) || type.equals(ServerType.WARFACTORY)) {
				tryQNA(event,type,data);
				tryAnswerGeneral(event,type,data);
			}
			String message = data.getContentDisplay();
			if (message.equals("?quickscan")) {
				Message ref = data.getReferencedMessage();
				if (ref == null)
					chan.sendMessage("you have to reply to a message containing logs").queue();
				else {
					if (System.currentTimeMillis() < lastCommandUse+10000)
						chan.sendMessage("I can't keep up! ("+((lastCommandUse+10000-System.currentTimeMillis())/1000)+"s left)").queue();
					lastCommandUse = System.currentTimeMillis();
					tryQuickScan(data,ref,chan,true,true);
				}
			} else if (message.startsWith("?answer ")) {
				if (System.currentTimeMillis() < lastCommandUse+10000)
					chan.sendMessage("I can't keep up! ("+((lastCommandUse+10000-System.currentTimeMillis())/1000)+"s left)").queue();
				else {
					String tag = message.substring("?answer ".length());
					if (tag.equalsIgnoreCase("list")) {
						StringBuilder sb = new StringBuilder();
						boolean first = true;
						for (String s : Responses.tags.keySet()) {
							if (!first)
								sb.append(", ");
							sb.append(s);
							first = false;
						}
						chan.sendMessage("tags: "+sb.toString()).queue();
					} else if (Responses.tags.containsKey(tag)) {
						lastCommandUse = System.currentTimeMillis();
						chan.sendMessage(append(Responses.tags.get(tag).get(),"\n\nHope this helps!").build()).queue();
					} else
						chan.sendMessage("no tag "+tag+" found").queue();
				}
			}
			//if (true) return;
			//event.getMessage().getChannel().sendMessage("Channel class: "+chan.getClass().getName()).queue();
			//event.getMessage().getChannel().sendMessage("name: "+impl.getName()+", count: "+impl.getTotalMessageCount()).queue();
			//event.getMessage().getChannel().sendMessage("forum name: "+forum.getName()).queue();
		}
		public static boolean tryQuickScan(Message data,Message target,MessageChannel chan,boolean wasForced,boolean shouldSendSuccessMessage) {
			String url = null;
			for (MessageEmbed embed : target.getEmbeds())
				url = embed.getUrl();
			for (Attachment attachment : target.getAttachments())
				url = attachment.getUrl();
			if (url != null)
				return diagnoseLog(url,data,chan,wasForced,shouldSendSuccessMessage);
			else if (wasForced)
				chan.sendMessage("that message ain't logs!").queue();
			return false;
		}
		public static final Pattern modListPattern = Pattern.compile(".*\\|\\s*LC\\w*\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\S*)\\s*\\|.*\\|.*\\|.*");
		public static boolean diagnoseLog(String url,Message data,MessageChannel chan,boolean wasForced,boolean shouldSendSuccessMessage) {
			System.out.println("Diagnosing link "+url);
			List<String> lines = readFromURL(url);
			if (lines == null) {
				if (wasForced)
					chan.sendMessage(MessageCreateData.fromContent("are you sure that's a log?")).queue();
				return false;
			}
			boolean good = false;
			boolean isCrash = false;
			Map<String,String> modlist = new HashMap<>();
			for (String line : lines) {
				System.out.println(line);
				if (line.contains("main/INFO") || line.contains("main/WARN") || line.contains("main/ERROR"))
					good = true;
				if (line.contains("Minecraft Crash Report") || line.contains("System Details"))
					isCrash = true;
				Matcher matcher = modListPattern.matcher(line);
				if (matcher.matches()) {
					//System.out.println("Analyzing modlist, data: "+matcher.group(1)+": "+matcher.group(2));
					modlist.put(matcher.group(1),matcher.group(2));
				}
			}
			if (!good && wasForced && !isCrash) {
				chan.sendMessage(MessageCreateData.fromContent("that doesn't look like a Minecraft log")).queue();
				return false;
			}
			if (!isCrash) {
				if (wasForced)
					chan.sendMessage(MessageCreateData.fromContent("I couldn't find any crash information in it")).queue();
				return false;
			}
			String prefix = "";
			if (modlist.containsKey("hbm")) {
				{ // missing mods
					boolean isMissingMixin = false;
					boolean isMissingCTM = false;
					if (!modlist.containsKey("mixinbooter"))
						isMissingMixin = true;
					if (!modlist.containsKey("ctm"))
						isMissingCTM = true;
					if (/*isMissingCTM || */isMissingMixin) {
						String mods = "";
						if (isMissingMixin)
							mods = "MixinBooter";
						if (isMissingCTM) {
							if (isMissingMixin)
								mods = mods + " and ";
							mods = mods + "ConnectedTexturesMod";
						}
						chan.sendMessage(MessageCreateData.fromContent("you need to get "+mods+" to run NTM:CE")).queue();
						prefix = "also ";
					}
				}
				{ // base conflicts
					if (modlist.containsKey("essential")) {
						chan.sendMessage(MessageCreateData.fromContent(prefix+"you need to get rid of Essential, that mod is not compatible with NTM:CE because it shadows mixins")).queue();
						prefix = "also ";
					}
				}
				if (modlist.containsKey("leafia")) { // LCA conflicts
					if (modlist.containsKey("tickcentral")) {
						chan.sendMessage(MessageCreateData.fromContent(prefix+"TickCentral is not compatible with Leafia's Cursed Addon")).queue();
						prefix = "also ";
					}
					if (modlist.containsKey("entityculling")) {
						chan.sendMessage(MessageCreateData.fromContent(prefix+"EntityCulling conflicts with Leafia's Cursed Addon for some reason")).queue();
						prefix = "also ";
					}
				}
				if (prefix.isEmpty() && shouldSendSuccessMessage)
					chan.sendMessage(MessageCreateData.fromContent("I did a quick scan for common causes, couldn't find any issues there")).queue();
			}
			return true;
		}
		public static List<String> readFromURL(String link) {
			try {
				URL url = new URL(link);
				URLConnection conn = url.openConnection();
				conn.setConnectTimeout(5000);
				conn.setReadTimeout(5000);
				BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),StandardCharsets.UTF_8));
				String line;
				List<String> lines = new ArrayList<>();
				while ((line = reader.readLine()) != null) {
					for (byte b : line.getBytes()) {
						if (b == 0)
							return null;
					}
					lines.addAll(Arrays.asList(line.split("<a")));
				}
				return lines;
			} catch (Exception e) {
				e.printStackTrace();
			}
			return new ArrayList<>();
		}
		/// why do people do this?
		public static void tryAnswerGeneral(MessageReceivedEvent event,ServerType type,Message data) {
			MessageChannelUnion chan = event.getChannel();
			if (chan.getName().equalsIgnoreCase("warfactory-general")) {
				String msg = data.getContentDisplay().toLowerCase();
				if (containsByFuncs(msg,QuestionDetector::howTo,QuestionDetector::anyWayTo)) {
					if (containsRegexes(msg,"he","convert to") && containsRegexes(msg,"rf","fe"))
						chan.sendMessage(append(Responses.qnaConverter(),"\n\nAlso please don't ask questions in https://discord.com/channels/1241479482964054057/1273376849283645470.").build()).queue();
				}
				tryQuickScan(data,data,chan,false,false);
			}
		}
		public static void tryQNA(MessageReceivedEvent event,ServerType type,Message data) {
			MessageChannelUnion chan = event.getChannel();
			if (chan instanceof ThreadChannelImpl thread) {
				if (thread.getParentChannel() instanceof ForumChannel forum) {
					// if on development server, reply infinitely
					// if on actual WF server, only reply on the first message of the thread
					if (thread.getMessageCount() <= 1 || type.equals(ServerType.DEVELOPMENT)) {
						if (forum.getName().equalsIgnoreCase("issues-and-qna"))
							answerQNA(data,thread,event);
					} else
						tryQuickScan(data,data,chan,false,false);
				}
			}
		}
		public static void answerQNA(Message data,ThreadChannelImpl thread,MessageReceivedEvent event) {
			String msg = data.getContentDisplay().toLowerCase();
			String title = thread.getName();
			if (threadContainsRegexes(title,msg,"he") && threadContainsRegexes(title,msg,"rf","fe"))
				thread.sendMessage(append(Responses.qnaConverter(),"\n\nHope this helps!").build()).queue();
			else if (threadContainsRegexes(title,msg,"crash")) {
				if (!tryQuickScan(data,data,thread,false,true))
					thread.sendMessage(MessageCreateData.fromContent("please provide logs if you haven't, we cannot do anything without it")).queue();
			}
		}
	}
	public static MessageCreateBuilder append(MessageCreateBuilder c,String... ss) {
		StringBuilder s1 = new StringBuilder(c.getContent());
		for (String s : ss)
			s1.append(s);
		c.setContent(s1.toString());
		return c;
	}
	public static FileUpload getUpload(String resource) {
		String[] a = resource.split("/");
		return FileUpload.fromData(getStream(resource),a[a.length-1]);
	}
	public static InputStream getStream(String resource) {
		return SillyBot.class.getClassLoader().getResourceAsStream(resource);
	}
	public static boolean threadContainsRegexes(String title,String msg,String... w) {
		for (String ws : w) {
			if (containsRegex(title,ws))
				return true;
			if (containsRegex(msg,ws))
				return true;
		}
		return false;
	}
	public static boolean containsRegexes(String s,String... w) {
		for (String ws : w) {
			if (containsRegex(s,ws))
				return true;
		}
		return false;
	}
	public static boolean containsAllRegexes(String s,String... w) {
		for (String ws : w) {
			if (!containsRegex(s,ws))
				return false;
		}
		return true;
	}
	public static boolean containsRegex(String s,String w) {
		return (" "+s.toLowerCase()+" ").matches(".*\\W"+w+"\\W.*");
	}
	@SafeVarargs
	public static boolean containsByFuncs(String s,Function<String,Boolean>... funcs) {
		for (Function<String,Boolean> func : funcs) {
			if (func.apply(s))
				return true;
		}
		return false;
	}
	static void main(String[] args) {
		try {
			if (!debug) {
				try {
					System.out.println("The bot is on release mode. Are you sure want to continue? (Y/N)");
					BufferedReader r = new BufferedReader(new InputStreamReader(System.in));
					String s = r.readLine();
					if (!s.equalsIgnoreCase("y")) {
						throw new RuntimeException("Execution Cancelled");
					}
					r.close();
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			String token = Files.readString(tokenDir.toPath());
			List<GatewayIntent> intents = new ArrayList<>(EnumSet.allOf(GatewayIntent.class));
			// remove unnecessary privileged intents
			intents.remove(GatewayIntent.GUILD_PRESENCES);
			intents.remove(GatewayIntent.GUILD_MEMBERS);
			jda = JDABuilder.createDefault(token)
					.enableIntents(intents)
					.addEventListeners(new SillyListener())
					.setStatus(debug ? OnlineStatus.IDLE : OnlineStatus.ONLINE)
					.build();
			self = jda.getSelfUser();
			jda.awaitReady();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
