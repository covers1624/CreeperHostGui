package net.creeperhost.minetogether.events;

import net.creeperhost.minetogether.MineTogether;
import net.creeperhost.minetogether.api.Order;
import net.creeperhost.minetogether.client.gui.GuiGDPR;
import net.creeperhost.minetogether.client.gui.chat.ingame.GuiChatOurs;
import net.creeperhost.minetogether.client.gui.chat.ingame.GuiNewChatOurs;
import net.creeperhost.minetogether.client.gui.element.GuiButtonCreeper;
import net.creeperhost.minetogether.client.gui.order.GuiGetServer;
import net.creeperhost.minetogether.client.gui.serverlist.gui.GuiMultiplayerPublic;
import net.creeperhost.minetogether.config.Config;
import net.creeperhost.minetogether.lib.ModInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.MainMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepInMultiplayerScreen;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.resources.I18n;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.ObfuscationReflectionHelper;
import net.minecraftforge.fml.event.server.ServerLifecycleEvent;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

@Mod.EventBusSubscriber(modid = ModInfo.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ScreenEvents
{
    boolean first = true;
    
    @SubscribeEvent
    public void openScreen(GuiScreenEvent.InitGuiEvent.Post event)
    {
        if (event.getGui() instanceof MainMenuScreen)
        {
            if (!MineTogether.instance.gdpr.hasAcceptedGDPR())
            {
                Minecraft.getInstance().currentScreen = new GuiGDPR(event.getGui());
            } else
            {
                if (first)
                {
                    first = false;
                    MineTogether.proxy.startChat();
                }
            }
            
            if (Config.getInstance().isServerListEnabled() || Config.getInstance().isChatEnabled())
            {
                MineTogether.instance.setRandomImplementation();
                
                event.addWidget(new GuiButtonCreeper(event.getGui().width / 2 - 124, event.getGui().height / 4 + 96, p ->
                {
                    Minecraft.getInstance().displayGuiScreen(GuiGetServer.getByStep(0, new Order()));
                }));
            }
            //Replace Multiplayer Button
            AtomicInteger width = new AtomicInteger();
            AtomicInteger height = new AtomicInteger();
            AtomicInteger x = new AtomicInteger();
            AtomicInteger y = new AtomicInteger();
            
            event.getWidgetList().forEach(widget ->
            {
                if (widget instanceof Button)
                {
                    Button button = (Button) widget;
                    //Get the translated name so we can make sure to remove the correct button
                    String name = I18n.format("menu.multiplayer");

                    if (button.getMessage().equalsIgnoreCase(name))
                    {
                        width.set(button.getWidth());
                        height.set(button.getHeight());
                        x.set(button.x);
                        y.set(button.y);
                        
                        button.active = false;
                        button.visible = false;
                    }
                }
            });
            
            event.addWidget(new Button(x.get(), y.get(), width.get(), height.get(), I18n.format("menu.multiplayer"), p ->
            {
                Minecraft.getInstance().displayGuiScreen(new GuiMultiplayerPublic(event.getGui()));
            }));
        }
    }

    boolean firstOpen = true;

    @SubscribeEvent
    public void openScreen(GuiOpenEvent event)
    {
        Screen screen = event.getGui();

        if (screen instanceof ChatScreen && Config.getInstance().isChatEnabled() && !MineTogether.instance.ingameChat.hasDisabledIngameChat())
        {
            String presetString = "";
            boolean sleep = false;
            if (screen instanceof SleepInMultiplayerScreen)
            {
                sleep = true;
            }

            ChatScreen chatScreen = (ChatScreen) event.getGui();
            presetString = chatScreen.defaultInputFieldText;
            MinecraftServer minecraftServerInstance = MineTogether.server;

            if (Config.getInstance().isAutoMT() && minecraftServerInstance != null && minecraftServerInstance.isSinglePlayer() && Minecraft.getInstance().ingameGUI.getChatGUI() instanceof GuiNewChatOurs && firstOpen)
            {
                firstOpen = false;
                ((GuiNewChatOurs) Minecraft.getInstance().ingameGUI.getChatGUI()).setBase(!MineTogether.instance.gdpr.hasAcceptedGDPR());
            }
            try
            {
                event.setGui(new GuiChatOurs(presetString, sleep));
            } catch (Exception ignored){}
        }
    }
}
