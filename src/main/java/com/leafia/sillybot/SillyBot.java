package com.leafia.sillybot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.Attachment;
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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Attribute;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
	private static final boolean debug = false;
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
			if (name.equalsIgnoreCase("ntm cursed"))
				return ServerType.CURSED;
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
			String message = data.getContentDisplay();

			if (data.getContentRaw().contains("<@1514985371711176906>")) {
				chan.sendMessage("mrow~").queue();
				return;
			}
			Message ref = data.getReferencedMessage();
			if (ref != null) {
				if (ref.getAuthor().equals(self)) {
					if (message.toLowerCase().equals("thx") || message.toLowerCase().equals("ty") || message.toLowerCase().contains("thank")) {
						chan.sendMessage("you're welcome :3c").queue();
						return;
					} else if (message.toLowerCase().contains("stupid clanker")) {
						chan.sendMessage("sorry ;(").queue();
						return;
					}
				}
			}

			if (type.equals(ServerType.DEVELOPMENT) || type.equals(ServerType.WARFACTORY)) {
				tryQNA(event,type,data);
			}
			tryAnswerGeneral(event,type,data);
			if (message.equals("?quickscan")) {
				if (ref == null) {
					if (!data.getAttachments().isEmpty()) {
						if (System.currentTimeMillis() < lastCommandUse+10000) {
							chan.sendMessage("I can't keep up! ("+((lastCommandUse+10000-System.currentTimeMillis())/1000)+"s left)").queue();
							return;
						}
						lastCommandUse = System.currentTimeMillis();
						tryQuickScan(data,data,chan,true,true);
					} else
						chan.sendMessage("you have to reply to a message containing logs").queue();
				} else {
					if (System.currentTimeMillis() < lastCommandUse+10000) {
						chan.sendMessage("I can't keep up! ("+((lastCommandUse+10000-System.currentTimeMillis())/1000)+"s left)").queue();
						return;
					}
					lastCommandUse = System.currentTimeMillis();
					tryQuickScan(data,ref,chan,true,true);
				}
				return;
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
				return;
			}
			//tryConversation(event,data);
			//if (true) return;
			//event.getMessage().getChannel().sendMessage("Channel class: "+chan.getClass().getName()).queue();
			//event.getMessage().getChannel().sendMessage("name: "+impl.getName()+", count: "+impl.getTotalMessageCount()).queue();
			//event.getMessage().getChannel().sendMessage("forum name: "+forum.getName()).queue();
		}
		/*
		public static boolean tryConversation(MessageReceivedEvent event,Message data) {
			Message ref = data.getReferencedMessage();
			if (ref == null) {
				try {
					User IAsked = null;
					for (Message r2 : event.getChannel().getHistory().retrievePast(5).submit().get()) {
						if (r2.getAuthor() == self) {
							Message r3 = r2.getReferencedMessage();
							if (r3 != null)
								IAsked = r3.getAuthor();
						} else if (IAsked != null) {

						}
					}
				} catch (Exception ignored) { }
			}
		}*/ // yeah screw it im keeping my bot simple
		public static boolean tryQuickScan(Message data,Message target,MessageChannel chan,boolean wasForced,boolean shouldSendSuccessMessage) {
			String url = null;
			int priority = 0;
			for (MessageEmbed embed : target.getEmbeds()) {
				int curPriority = 0;
				String curUrl = embed.getUrl();
				if (curUrl != null) {
					if (curUrl.contains("mclo.gs") || curUrl.contains("gnomebot.dev"))
						curPriority = 2;
					if (curPriority >= priority) {
						url = curUrl;
						priority = curPriority;
					}
				}
			}
			for (Attachment attachment : target.getAttachments()) {
				int curPriority = 0;
				String curUrl = attachment.getUrl();
				if (curUrl.endsWith(".txt"))
					curPriority = 1;
				if (curPriority >= priority) {
					url = curUrl;
					priority = curPriority;
				}
			}
			if (url != null) {
				LogDiagnosisReturnCode code = diagnoseLog(url,data,chan,wasForced,shouldSendSuccessMessage);
				if (code.equals(LogDiagnosisReturnCode.ANSWERED) || code.equals(LogDiagnosisReturnCode.INVALID_ANSWERED))
					chan.sendMessage("please provide logs again if it continues to crash\nif I don't respond automatically, you can use the ?quickscan command to wake me up").queue();
				return !code.equals(LogDiagnosisReturnCode.INVALID) && !code.equals(LogDiagnosisReturnCode.INVALID_ANSWERED);
			} else if (wasForced) {
				if (target.getAuthor().equals(self))
					chan.sendMessage("a-are you scanning ME?!").queue();
				else {
					if (target.getContentDisplay().contains("scan")) {
						long id = target.getAuthor().getIdLong();
						long var = id%10;
						switch((int)var) {
							case 0,5 -> chan.sendMessage("probably a human").queue();
							case 1,6 -> chan.sendMessage("probably not a human").queue();
							case 2,7 -> chan.sendMessage("definitely a furry :3c").queue();
							case 3,8 -> chan.sendMessage("their base is probably a lawnbase").queue();
							case 4,9 -> chan.sendMessage("they probably don't play minecraft").queue();
						}
					} else
						chan.sendMessage("that message ain't logs!\nmake sure the log is embedded or uploaded as an attachment").queue();
				}
			}
			return false;
		}
		public static final Pattern modListPattern = Pattern.compile(".*\\|\\s*L\\w*\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\S*)\\s*\\|\\s*(\\S*)\\s*\\|.*");
		public static class ModInfo {
			public final String version;
			public final String filename;
			public ModInfo(String version,String filename) {
				this.filename = filename;
				this.version = version;
			}
		}
		public static final Pattern errorPattern = Pattern.compile("\\[\\d+:\\d+:\\d+\\]\\s*\\[(\\w+)/(\\w+)\\].*");
		public enum ErrorType { ERROR,FATAL }
		public static class ErrorData {
			public ErrorType type;
			public String thread = "Unknown";
			public final List<ErrorStack> stack = new ArrayList<>();
		}
		public static class ErrorStack {
			public String title;
			public final List<String> stacktrace = new ArrayList<>();
		}
		public enum LogDiagnosisReturnCode {
			INVALID,INVALID_ANSWERED,OUT_OF_SUPPORT,ANSWERED,SUCCESS
		}
		public static LogDiagnosisReturnCode diagnoseLog(String url,Message data,MessageChannel chan,boolean wasForced,boolean shouldSendSuccessMessage) {
			//System.out.println("Diagnosing link "+url);
			List<String> lines = readFromURL(url);
			if (lines == null) {
				if (wasForced)
					chan.sendMessage(MessageCreateData.fromContent("are you sure that's a log?")).queue();
				return LogDiagnosisReturnCode.INVALID;
			}
			boolean isFullLog = false;
			boolean isCrash = false;
			Map<String,ModInfo> modlist = new HashMap<>();
			boolean otherNTMeditions = false;
			List<ErrorData> errorDatas = new ArrayList<>();
			ErrorData curBuilding = null;
			ErrorStack buildingStack = null;
			int crashMessageDetectionPhase = 0;
			boolean readCrashMode = false;
			for (String line : lines) {
				if (line.contains("main/INFO") || line.contains("main/WARN") || line.contains("main/ERROR") || line.contains("main/FATAL"))
					isFullLog = true;
				if (line.startsWith("["))
					readCrashMode = false;
				if (line.contains("Minecraft Crash Report")) {
					isCrash = true;
					readCrashMode = true;
				}
				if (curBuilding != null) {
					if (line.trim().startsWith("Caused by: ")) {
						String cause = line.substring(line.indexOf("Caused by: ")+"Caused by: ".length());
						buildingStack = new ErrorStack();
						curBuilding.stack.add(buildingStack);
						buildingStack.title = cause;
					}
					if (line.startsWith("[") || line.isBlank()) {
						if (curBuilding.stack.getFirst().title != null)
							errorDatas.add(curBuilding);
						else {
							/*System.out.println("WARNING: Dismissed broken error data!");
							for (String s : curBuilding.stacktrace)
								System.out.println(s);*/
						}
						curBuilding = null;
					} else {
						if (buildingStack.title == null)
							buildingStack.title = line;
						else
							buildingStack.stacktrace.add(line.trim());
					}
				}
				if (readCrashMode) {
					if (crashMessageDetectionPhase == 0) {
						if (line.startsWith("Time:"))
							crashMessageDetectionPhase = 1;
					} else if (crashMessageDetectionPhase == 1) {
						if (line.startsWith("Description:"))
							crashMessageDetectionPhase = 2;
					} else if (crashMessageDetectionPhase == 2) {
						if (!line.isBlank()) {
							crashMessageDetectionPhase = 3;
							curBuilding = new ErrorData();
							curBuilding.type = ErrorType.FATAL;
							buildingStack = new ErrorStack();
							curBuilding.stack.add(buildingStack);
							buildingStack.title = line;
						}
					}
				} else {
					if (line.contains("/ERROR") || line.contains("/FATAL")) {
						Matcher matcher = errorPattern.matcher(line);
						if (matcher.matches()) {
							String thread = matcher.group(1);
							String type = matcher.group(2);
							curBuilding = new ErrorData();
							curBuilding.thread = thread;
							buildingStack = new ErrorStack();
							curBuilding.stack.add(buildingStack);
							if (type.equals("FATAL"))
								curBuilding.type = ErrorType.FATAL;
							else if (type.equals("ERROR"))
								curBuilding.type = ErrorType.ERROR;
							else
								throw new RuntimeException("Got unexpected error type "+type);
						}
					}
				}

				int index = line.indexOf("Minecraft Version: ");
				if (index != -1) {
					String minecraftVersion = line.substring("Minecraft Version: ".length()+index);
					if (!minecraftVersion.trim().equals("1.12.2")) {
						if (wasForced)
							chan.sendMessage(MessageCreateData.fromContent("I cannot help for NTM versions besides 1.12.2 (yours is "+minecraftVersion+")")).queue();
						return LogDiagnosisReturnCode.OUT_OF_SUPPORT;
					}
				}
				Matcher matcher = modListPattern.matcher(line);
				if (matcher.matches()) {
					//System.out.println("Analyzing modlist, data: "+matcher.group(1)+": "+matcher.group(2));
					modlist.put(matcher.group(1),new ModInfo(matcher.group(2),matcher.group(3)));
				}
			}
			if (!isFullLog && wasForced && !isCrash) {
				chan.sendMessage(MessageCreateData.fromContent("that doesn't look like a Minecraft log")).queue();
				return LogDiagnosisReturnCode.INVALID;
			}
			if (!isCrash) {
				if (wasForced)
					chan.sendMessage(MessageCreateData.fromContent("I couldn't find any crash information in it")).queue();
				if (diagnoseErrors(chan,errorDatas,wasForced))
					return LogDiagnosisReturnCode.INVALID_ANSWERED;
				return LogDiagnosisReturnCode.INVALID;
			}
			String prefix = "";
			Date date = new Date(System.currentTimeMillis());
			if (modlist.containsKey("hbm")) {
				String fn = modlist.get("hbm").filename.toLowerCase();
				if (fn.matches("hbm\\d+\\.\\d+\\.\\d+a?(-g)?.*") || fn.matches("hbm.a.*") || fn.matches("hbm.g.*")) {
					chan.sendMessage(MessageCreateData.fromContent("SOMEONE PLAYS RELOADED IN "+(date.getYear()+1900)+"??")).queue();
					chan.sendMessage(MessageCreateData.fromContent("PLEASE get [Community Edition](https://www.curseforge.com/minecraft/mc-mods/hbm-nuclear-tech-mod-community-edition)")).queue();
				} else if (fn.contains("waldemar") || fn.matches(".*well.forged.*")) {
					chan.sendMessage(MessageCreateData.fromContent("that's waldemar edition..")).queue();
					chan.sendMessage(MessageCreateData.fromContent("please tell me that's a joke.")).queue();
					chan.sendMessage(MessageCreateData.fromContent("https://www.curseforge.com/minecraft/mc-mods/hbm-nuclear-tech-mod-community-edition")).queue();
				} else if (fn.matches(".*hamster.reloaded.*")) {
					chan.sendMessage(MessageCreateData.fromContent("that's hamster reloaded")).queue();
					chan.sendMessage(MessageCreateData.fromContent("are you messing with me?")).queue();
					chan.sendMessage(MessageCreateData.fromContent("https://www.curseforge.com/minecraft/mc-mods/hbm-nuclear-tech-mod-community-edition")).queue();
				} else if (fn.contains("extended"))
					chan.sendMessage(MessageCreateData.fromContent("that's extended edition, get [Community Edition](https://www.curseforge.com/minecraft/mc-mods/hbm-nuclear-tech-mod-community-edition)")).queue();
				else if (fn.matches("ntm.cursed.edition.*")) // almost forgot
					chan.sendMessage(MessageCreateData.fromContent("legacy cursed edition is out of support, get [CE](https://www.curseforge.com/minecraft/mc-mods/hbm-nuclear-tech-mod-community-edition) and [LCA](https://github.com/ntmleafia/NTM-Cursed-Addon/releases) instead (your world will not fully convert though)")).queue();
				else {
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
					if (!otherNTMeditions) { // version check
						List<String> linesGH = readFromURL("https://raw.githubusercontent.com/Warfactory-Offical/Hbm-s-Nuclear-Tech-CE/refs/heads/master/gradle.properties");
						for (String s : linesGH) {
							if (s.trim().startsWith("modVersion")) {
								String version = s.split("=")[1].trim();
								if (!modlist.get("hbm").version.matches(version)) {
									chan.sendMessage(MessageCreateData.fromContent(prefix+"that doesn't look like the latest version of NTM:CE, please upgrade unless I'm stupid")).queue();
									prefix = "also ";
								}
								break;
							}
						}
					}
					if (prefix.isEmpty()) {
						if (shouldSendSuccessMessage)
							chan.sendMessage(MessageCreateData.fromContent("I did a quick scan for common causes, couldn't find any issues there")).queue();
						if (diagnoseErrors(chan,errorDatas,wasForced))
							return LogDiagnosisReturnCode.ANSWERED;
						return LogDiagnosisReturnCode.SUCCESS;
					}
					return LogDiagnosisReturnCode.ANSWERED;
				}
			} else {
				if (wasForced)
					chan.sendMessage(MessageCreateData.fromContent("I cannot scan for crashes that does not relate to NTM:CE")).queue();
			}
			return LogDiagnosisReturnCode.OUT_OF_SUPPORT;
		}
		public static String[] split2(String s,String sep) {
			String[] out = new String[2];
			int index = s.indexOf(sep);
			out[0] = s.substring(0,index);
			out[1] = s.substring(index+1);
			return out;
		}
		public static boolean diagnoseErrors(MessageChannel chan,List<ErrorData> errorDatas,boolean wasForced) {
			boolean first = true;
			boolean responded = false;
			for (ErrorData edat : errorDatas) {
				String sendMsg = null;
				if (edat.type == ErrorType.FATAL) {
					int stackCounter = -1;
					for (ErrorStack stack : edat.stack) {
						if (sendMsg != null) break;
						stackCounter++;
						String type = stack.title;
						String message = "";
						if (stack.title.contains(":")) {
							String[] spl = split2(stack.title,":");
							type = spl[0];
							message = spl[1];
						}
						String[] exceptionClassPath = type.split("\\.");
						String name = exceptionClassPath[exceptionClassPath.length-1];
						if (name.equals("ConcurrentModificationException"))
							sendMsg = "-# "+stack.title+"\nConcurrentModificationException detected, it might be a luck-based crash\ntry joining the world again, it might just go as if nothing happened";
						else if (name.toLowerCase().contains("mixin")) {
							if (name.equals("MixinTargetAlreadyLoadedException") && !message.isBlank()) {
								int indexDot = message.indexOf(".");
								String mod = message.substring(0,indexDot);
								int indexSpace = mod.lastIndexOf(" ");
								mod = mod.substring(indexSpace+1);
								if (!mod.equals("hbm")) // suggesting to remove ntm would be retarded
									sendMsg = "-# "+stack.title+"\nMixin related error, possibly mod conflict\nthe offending mod might be "+mod+", try getting rid of it";
							} else {
								sendMsg = "-# "+stack.title+"\nMixin related error, but I couldn't get the specific reason for it\nBut this is most likely a mod conflict";
							}
						} else if (name.equals("NoSuchMethodError") || name.equals("NoSuchFieldError")) {
							if (!message.contains("hbm")) {
								String possibleModName = "?";
								System.out.println(message);
								try {
									possibleModName = message.split("\\.")[1]+"(?)";
								} catch (ArrayIndexOutOfBoundsException ignored) {} // i'm lazy
								sendMsg = "-# "+stack.title+"\nthe mod is trying to access something from aforementioned mod ("+possibleModName+"), but no such thing exists\nthis could mean you're using a version of "+possibleModName+" that NTM does not intend to work with\nconsider upgrading their mod if there are any updates";
							}
						}
					}
				}
				if (sendMsg != null) {
					if (first)
						chan.sendMessage(MessageCreateData.fromContent("here are possible solutions:")).queue();
					first = false;
					chan.sendMessage(MessageCreateData.fromContent(sendMsg)).queue();
					responded = true;
				}
			}
			return responded;
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
				StringBuilder concentrate = new StringBuilder();
				while ((line = reader.readLine()) != null) {
					for (byte b : line.getBytes()) {
						if (b == 0)
							return null;
					}
					/*
					if (line.contains("http") && line.contains("href") && line.toLowerCase().contains("raw")) {

					}
					lines.addAll(Arrays.asList(line.split("<a")));*/
					// fuck it we do html
					lines.add(line);
					if (!concentrate.isEmpty())
						concentrate.append("\n");
					concentrate.append(line);
				}
				String s = concentrate.toString();
				// if it's html shit, try to get raw content
				if (s.contains("html")) {
					Document doc = Jsoup.parse(s);
					for (Element element : doc.body()) {
						if (element.html().toLowerCase().contains("raw")) {
							Attribute attr = element.attribute("href");
							if (attr != null) {
								String lnk = attr.getValue();
								if (lnk.contains("http"))
									return readFromURL(lnk);
							}
						}
					}
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
			if (chan.getName().equalsIgnoreCase("warfactory-general") && (type == ServerType.DEVELOPMENT || type == ServerType.WARFACTORY)) {
				String msg = data.getContentDisplay().toLowerCase();
				if (containsByFuncs(msg,QuestionDetector::howTo,QuestionDetector::anyWayTo)) {
					if (containsRegexes(msg,"he","convert to") && containsRegexes(msg,"rf","fe"))
						chan.sendMessage(append(Responses.qnaConverter(),"\n\nAlso please don't ask questions in https://discord.com/channels/1241479482964054057/1273376849283645470.").build()).queue();
				}
				tryQuickScan(data,data,chan,false,false);
			} else if ((chan.getName().equalsIgnoreCase("general") || chan.getName().equalsIgnoreCase("ntm-questions") || chan.getName().equalsIgnoreCase("bot-commands")) && (type == ServerType.DEVELOPMENT || type == ServerType.CURSED)) {
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
			JDABuilder builder = JDABuilder.createDefault(token)
					.enableIntents(intents)
					.addEventListeners(new SillyListener())
					.setStatus(debug ? OnlineStatus.IDLE : OnlineStatus.ONLINE);
			if (debug)
				builder.setActivity(Activity.customStatus("Under enhancement"));
			jda = builder.build();
			self = jda.getSelfUser();
			jda.awaitReady();
			/*
			jda.updateCommands()
					.addCommands(Commands.message("Quick Scan"))
					.queue();*/ // fuck you forget it
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
