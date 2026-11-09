package com.sp.entity.client.model;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import com.sp.DestroyingMinecraft;
import net.minecraft.resources.ResourceLocation;

public class StarPiercerModel {
    public static final ModelLayerLocation STAR_PIERCER_MODEL_LAYER = new ModelLayerLocation(DestroyingMinecraft.idOf("star_piercer"), "main");
    public static LayerDefinition getTexturedModelData() { return LayerDefinition.create(net.minecraft.client.model.geom.builders.MeshDefinition.create(), 64, 64); }
}
