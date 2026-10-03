package com.kuronami.jadeftbclaims.client;

import com.kuronami.jadeftbclaims.ftb.ClaimInfo;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import snownee.jade.api.ITooltip;
import snownee.jade.api.ui.IElement;
import snownee.jade.api.ui.IElementHelper;

import java.util.ArrayList;
import java.util.List;

/** Builds the original claim row and adds a face beside the colored team name when known. */
public final class ClaimTooltip {
    public static final String KEY_CLAIMED = "tooltip.jadeftbclaims.claimed";
    public static final String KEY_FORCE_LOADED = "tooltip.jadeftbclaims.force_loaded";
    private ClaimTooltip() {}

    public static Component line(ClaimInfo info) {
        MutableComponent line = Component.translatable(KEY_CLAIMED, info.teamName()).withStyle(ChatFormatting.GRAY);
        if (info.forceLoaded()) line = line.append(Component.literal(" "))
                .append(Component.translatable(KEY_FORCE_LOADED).withStyle(ChatFormatting.DARK_AQUA));
        return line;
    }

    public static void add(ITooltip tooltip, ClaimInfo info) {
        if (info.ownerProfile() == null) {
            tooltip.add(line(info));
            return;
        }
        List<IElement> row = new ArrayList<>(4);
        row.add(IElementHelper.get().text(Component.translatable(KEY_CLAIMED, info.teamName())
                .withStyle(ChatFormatting.GRAY)));
        row.add(IElementHelper.get().spacer(2, 0));
        row.add(new OwnerFaceElement(info.ownerProfile()));
        if (info.forceLoaded()) row.add(IElementHelper.get().text(Component.literal(" ")
                .append(Component.translatable(KEY_FORCE_LOADED).withStyle(ChatFormatting.DARK_AQUA))));
        tooltip.add(row);
    }
}
