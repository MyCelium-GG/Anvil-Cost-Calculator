package my.celium.org.anvilcostcalculator;

import java.net.URI;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/**
 * What happens when the mod is installed without Mycel: the game still starts, the mod stays idle,
 * and each time the player joins a world a chat line says Mycel is missing, with a link to download it.
 *
 * <p>This class must never touch a Mycel class: it is the only part of the mod that runs when
 * Mycel is not there. Only vanilla classes are used.
 */
public final class MissingMycel {
    static final String NAME = "Anvil Cost Calculator";
    public static final String MYCEL_URL = "https://www.curseforge.com/minecraft/mc-mods/mycel";

    private static final int RED = 0xFF3B3B;
    private static final int BODY = 0xF4F4F5;
    private static final int FAINT = 0x9A9AA3;
    private static final int LINK = 0xFBBF24;

    private static boolean logged;
    /** The connection we last told; a respawn makes a new player but keeps the connection. */
    private static Object told;


    private MissingMycel() {
    }

    /** One warning in the log, so owners see it without joining. */
    public static synchronized void logOnce() {
        if (!logged) {
            logged = true;
            LogUtils.getLogger().warn("{} is installed without Mycel and does nothing until Mycel is installed: {}",
                    NAME, MYCEL_URL);
        }
    }

    /** Called every client tick: tells the player once each time they join a world. */
    public static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Object connection = minecraft.getConnection();
        if (player != null && connection != null && connection != told) {
            told = connection;
            player.sendSystemMessage(message());
        }
    }

    /** {@code Anvil Cost Calculator » Mycel is missing. ... [Download Mycel]} */
    public static Component message() {
        MutableComponent link = Component.translatableWithFallback("anvilcostcalculator.missing_mycel.download", "[Download Mycel]")
                .withStyle(Style.EMPTY
                .withColor(LINK)
                .withUnderlined(true)
                .withClickEvent(new ClickEvent.OpenUrl(URI.create(MYCEL_URL)))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(MYCEL_URL))));
        return Component.empty()
                .append(Component.literal(NAME).withStyle(Style.EMPTY.withColor(RED).withBold(true)))
                .append(Component.literal(" » ").withStyle(Style.EMPTY.withColor(FAINT)))
                .append(Component.translatableWithFallback("anvilcostcalculator.missing_mycel",
                        "Mycel is missing. %s needs the Mycel library to work and stays off until it is installed.", NAME)
                        .withStyle(Style.EMPTY.withColor(BODY)))
                .append(" ")
                .append(link);
    }
}
