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

/** 表示行の組み立て。判定に使う純粋な部分（テスト対象）。 */
public final class ClaimTooltip {

    public static final String KEY_CLAIMED = "tooltip.jadeftbclaims.claimed";
    public static final String KEY_FORCE_LOADED = "tooltip.jadeftbclaims.force_loaded";

    private ClaimTooltip() {}

    /** Adds the owner face beside the colored team label; preserves the original row when unknown. */
    public static void add(ITooltip tooltip, ClaimInfo info) {
        if (info.ownerProfile() == null) {
            tooltip.add(line(info));
            return;
        }
        List<IElement> row = new ArrayList<>(3);
        row.add(IElementHelper.get().text(Component.translatable(KEY_CLAIMED, info.teamName())
                .withStyle(ChatFormatting.GRAY)));
        row.add(IElementHelper.get().spacer(2, 0));
        row.add(new OwnerFaceElement(info.ownerProfile()));
        if (info.forceLoaded()) {
            row.add(IElementHelper.get().text(Component.literal(" ")
                    .append(Component.translatable(KEY_FORCE_LOADED).withStyle(ChatFormatting.DARK_AQUA))));
        }
        tooltip.add(row);
    }

    /**
     * 「Claimed: <team>」1行。force-load 中なら末尾に目印を添える。
     * team 名は FTB Teams の colored name（色付き Component）をそのまま渡す。
     */
    public static Component line(ClaimInfo info) {
        MutableComponent line = Component.translatable(KEY_CLAIMED, info.teamName())
                .withStyle(ChatFormatting.GRAY);
        if (info.forceLoaded()) {
            line = line.append(Component.literal(" "))
                    .append(Component.translatable(KEY_FORCE_LOADED)
                            .withStyle(ChatFormatting.DARK_AQUA));
        }
        return line;
    }
}
