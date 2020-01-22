package net.creeperhost.minetogether;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.creeperhost.minetogether.api.CreeperHostAPI;
import net.creeperhost.minetogether.api.ICreeperHostMod;
import net.creeperhost.minetogether.api.IServerHost;
import net.creeperhost.minetogether.chat.ChatHandler;
import net.creeperhost.minetogether.chat.Message;
import net.creeperhost.minetogether.client.gui.serverlist.data.Invite;
import net.creeperhost.minetogether.common.GDPR;
import net.creeperhost.minetogether.common.HostHolder;
import net.creeperhost.minetogether.common.IHost;
import net.creeperhost.minetogether.common.IngameChat;
import net.creeperhost.minetogether.config.Config;
import net.creeperhost.minetogether.config.ConfigHandler;
import net.creeperhost.minetogether.data.Friend;
import net.creeperhost.minetogether.events.ClientTickEvents;
import net.creeperhost.minetogether.events.ScreenEvents;
import net.creeperhost.minetogether.lib.ModInfo;
import net.creeperhost.minetogether.paul.Callbacks;
import net.creeperhost.minetogether.paul.CreeperHostServerHost;
import net.creeperhost.minetogether.proxy.Client;
import net.creeperhost.minetogether.proxy.IProxy;
import net.creeperhost.minetogether.proxy.Server;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

@Mod(value = ModInfo.MOD_ID)
public class MineTogether implements ICreeperHostMod, IHost
{
    public static final Logger logger = LogManager.getLogger("minetogether");
    public static ArrayList<String> mutedUsers = new ArrayList<>();
    public static ArrayList<String> bannedUsers = new ArrayList<>();
    public static IProxy proxy;
    public final Object inviteLock = new Object();
    public ArrayList<IServerHost> implementations = new ArrayList<IServerHost>();
    public IServerHost currentImplementation;
    public File configFile;
    public int curServerId = -1;
    public Invite handledInvite;
    public boolean active = true;
    public Invite invite;
    public GDPR gdpr;
    public IngameChat ingameChat;
    public String activeMinigame;
    public int minigameID;
    public boolean trialMinigame;
    public long joinTime;
    public String realName;
    public boolean online;
    
    //    private QueryGetter queryGetter;
    private String lastCurse = "";
    private Random randomGenerator;
    private CreeperHostServerHost implement;
    
    public String ourNick;
    public File mutedUsersFile;
    
    public static MineTogether instance;
    public static MinecraftServer server;

//    public HoverEvent.Action TIMESTAMP = EnumHelper.addEnum(HoverEvent.Action.class, "TIMESTAMP", new Class[]{String.class, boolean.class}, "timestamp_hover", true);
    
    public MineTogether()
    {
        instance = this;
        proxy = DistExecutor.runForDist(() -> Client::new, () -> Server::new);
        IEventBus eventBus = FMLJavaModLoadingContext.get().getModEventBus();
        eventBus.addListener(this::preInit);
        eventBus.addListener(this::preInitClient);
        eventBus.addListener(this::serverStarted);
        
        MinecraftForge.EVENT_BUS.register(new ScreenEvents());
        MinecraftForge.EVENT_BUS.register(this);
    }
    
    @SubscribeEvent
    public void preInit(FMLCommonSetupEvent event)
    {
        ConfigHandler.init();
        proxy.checkOnline();
        registerImplementation(new CreeperHostServerHost());
        
        proxy.registerKeys();

//        PacketHandler.packetRegister();
    }
    
    @SubscribeEvent
    public void preInitClient(FMLClientSetupEvent event)
    {
        File gdprFile = new File("local/minetogether/gdpr.txt");
        gdpr = new GDPR(gdprFile);
        
        HostHolder.host = this;
        File ingameChatFile = new File("local/minetogether/ingameChatFile.txt");
        ingameChat = new IngameChat(ingameChatFile);
        ourNick = "MT" + Callbacks.getPlayerHash(MineTogether.proxy.getUUID()).substring(0, 15);
        
        HashMap<String, String> jsonObj = new HashMap<>();
        
        int packID;
        
        try
        {
            packID = Integer.parseInt(Config.getInstance().curseProjectID);
        } catch (NumberFormatException e)
        {
            packID = -1;
        }
        
        jsonObj.put("p", String.valueOf(packID));
        
        Gson gson = new Gson();
        try //Temp fix until we cxan figure out why this fails
        {
            realName = gson.toJson(jsonObj);
        } catch (Exception ignored)
        {
        }
        
        MinecraftForge.EVENT_BUS.register(new ClientTickEvents());
    }
    
    @SubscribeEvent
    public void serverStarted(FMLServerStartingEvent event)
    {
        server = event.getServer();
//        event.registerServerCommand(new CommandKill());
    }
    
    @SuppressWarnings("Duplicates")
    public void saveConfig()
    {
        FileOutputStream configOut = null;
        try
        {
            configOut = new FileOutputStream(configFile);
            IOUtils.write(Config.saveConfig(), configOut);
            configOut.close();
        } catch (Throwable ignored)
        {
        } finally
        {
            try
            {
                if (configOut != null)
                {
                    configOut.close();
                }
            } catch (Throwable ignored)
            {
            }
        }
        
        if (Config.getInstance().isCreeperhostEnabled())
        {
            MineTogether.instance.implementations.remove(implement);
            implement = new CreeperHostServerHost();
            CreeperHostAPI.registerImplementation(implement);
        }
        
        if (!Config.getInstance().isCreeperhostEnabled())
        {
            MineTogether.instance.implementations.remove(implement);
            implement = null;
        }
    }
    
    public void updateCurse()
    {
        if (!Config.getInstance().curseProjectID.equals(lastCurse) && Config.getInstance().isCreeperhostEnabled())
        {
            Config.getInstance().setVersion(Callbacks.getVersionFromCurse(Config.getInstance().curseProjectID));
        }
        lastCurse = Config.getInstance().curseProjectID;
    }
    
    public void setRandomImplementation()
    {
        if (randomGenerator == null)
            randomGenerator = new Random();
        if (implementations.size() == 0)
        {
            currentImplementation = null;
            return;
        }
        int random = randomGenerator.nextInt(implementations.size());
        currentImplementation = implementations.get(random);
    }
    
    public IServerHost getImplementation()
    {
        return currentImplementation;
    }
    
    @Override
    public void registerImplementation(IServerHost serverHost)
    {
        implementations.add(serverHost);
    }

    @Override
    public ArrayList<Friend> getFriends()
    {
        return Callbacks.getFriendsList(false);
    }
    
    public final Object friendLock = new Object();
    public String friend = null;
    public boolean friendMessage = false;
    
    @Override
    public void friendEvent(String name, boolean isMessage)
    {
        synchronized (friendLock)
        {
            friend = ChatHandler.getNameForUser(name);
            friendMessage = isMessage;
        }
    }
    
    @Override
    public Logger getLogger()
    {
        return logger;
    }
    
    @Override
    public void messageReceived(String target, Message messagePair)
    {
        proxy.messageReceived(target, messagePair);
    }
    
    private static boolean anonLoaded = false;
    
    public String getNameForUser(String nick)
    {
        if (!anonLoaded)
        {
            File anonUsersFile = new File("local/minetogether/anonusers.json");
            InputStream anonUsersStream = null;
            try
            {
                String configString;
                if (anonUsersFile.exists())
                {
                    anonUsersStream = new FileInputStream(anonUsersFile);
                    configString = IOUtils.toString(anonUsersStream);
                } else
                {
                    anonUsersFile.getParentFile().mkdirs();
                    configString = "{}";
                }
                
                Gson gson = new Gson();
                ChatHandler.anonUsers = gson.fromJson(configString, new TypeToken<HashMap<String, String>>()
                {
                }.getType());
                ChatHandler.anonUsersReverse = new HashMap<>();
                for (Map.Entry<String, String> entry : ChatHandler.anonUsers.entrySet())
                {
                    ChatHandler.anonUsersReverse.put(entry.getValue(), entry.getKey());
                }
            } catch (Throwable ignored)
            {
            } finally
            {
                try
                {
                    if (anonUsersStream != null)
                    {
                        anonUsersStream.close();
                    }
                } catch (Throwable ignored)
                {
                }
            }
            anonLoaded = true;
        }
        
        if (nick.length() < 16)
            return null;
        
        nick = nick.substring(0, 17); // should fix where people join and get ` on their name for friends if connection issues etc
        if (ChatHandler.friends.containsKey(nick))
        {
            return ChatHandler.friends.get(nick);
        }
        if (nick.startsWith("MT"))
        {
            if (ChatHandler.anonUsers.containsKey(nick))
            {
                return ChatHandler.anonUsers.get(nick);
            } else
            {
                String anonymousNick = "User" + ChatHandler.random.nextInt(10000);
                while (ChatHandler.anonUsers.containsValue(anonymousNick))
                {
                    anonymousNick = "User" + ChatHandler.random.nextInt(10000);
                }
                ChatHandler.anonUsers.put(nick, anonymousNick);
                ChatHandler.anonUsersReverse.put(anonymousNick, nick);
                saveAnonFile();
                return anonymousNick;
            }
        }
        return null;
    }
    
    public void saveAnonFile()
    {
        Gson gson = new Gson();
        File anonUsersFile = new File("local/minetogether/anonusers.json");
        try
        {
            FileUtils.writeStringToFile(anonUsersFile, gson.toJson(ChatHandler.anonUsers));
        } catch (IOException ignored)
        {
        }
    }
    
    public void muteUser(String user)
    {
        mutedUsers.add(user);
        Gson gson = new Gson();
        try
        {
            FileUtils.writeStringToFile(mutedUsersFile, gson.toJson(mutedUsers));
        } catch (IOException ignored)
        {
        }
    }
    
    public void unmuteUser(String user)
    {
        String mtUser = ChatHandler.anonUsersReverse.get(user);
        mutedUsers.remove(mtUser);
        mutedUsers.remove(mtUser + "`");
        Gson gson = new Gson();
        try
        {
            FileUtils.writeStringToFile(mutedUsersFile, gson.toJson(mutedUsers));
        } catch (IOException ignored)
        {
        }
    }
    
    @Override
    public String getFriendCode()
    {
        return Callbacks.getFriendCode();
    }
    
    @Override
    public void acceptFriend(String friendCode, String name)
    {
        new Thread(() -> Callbacks.addFriend(friendCode, name)).start();
    }
    
    @Override
    public void closeGroupChat()
    {
        proxy.closeGroupChat();
    }
    
    @Override
    public void updateChatChannel()
    {
        proxy.updateChatChannel();
    }
    
    @Override
    public void userBanned(String username)
    {
        bannedUsers.add(username);
        proxy.refreshChat();
    }
}
