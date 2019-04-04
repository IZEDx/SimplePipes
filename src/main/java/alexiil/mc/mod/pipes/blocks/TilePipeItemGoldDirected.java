/*
 * Copyright (c) 2019 SpaceToad and the BuildCraft team
 * This Source Code Form is subject to the terms of the Mozilla Public License, v. 2.0. If a copy of the MPL was not
 * distributed with this file, You can obtain one at https://mozilla.org/MPL/2.0/
 */
package alexiil.mc.mod.pipes.blocks;

public class TilePipeItemGoldDirected extends TilePipe {
    public TilePipeItemGoldDirected() {
        super(SimplePipeBlocks.GOLD_DIRECTED_PIPE_ITEM_TILE, SimplePipeBlocks.GOLD_DIRECTED_PIPE_ITEMS, PipeFlowItem::new);
    }
}
