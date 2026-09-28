package com.kuronami.jadeftbclaims.ftb;

import net.minecraft.network.chat.Component;

/**
 * クレーム照会の結果。team 名は表示用に FTB Teams の colored name
 * （Component）のまま持つ。forceLoaded は force-load 中かどうか。
 */
public record ClaimInfo(Component teamName, boolean forceLoaded) {}
