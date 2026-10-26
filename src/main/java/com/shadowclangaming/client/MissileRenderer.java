package com.shadowclangaming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;


/**
 * A real 3D missile.
 *
 * The first build drew the missile as a spinning item sprite (vanilla's ThrownItemRenderer). That reads
 * as "thrown snowball", not "intercontinental ballistic missile". This renderer builds an actual model out
 * of boxes - booster skirt, main tube, warhead nose, four tail fins and four mid-body strakes - and
 * orients it along its own velocity so it visibly flies nose-first and tilts as it pitches over.
 *
 * Colouring is per warhead: the body stays painted metal, while the nose, band and fins are tinted with
 * the warhead's colour, so you can tell a decoy from an antimatter round across the sky. Tinting is
 * multiplication against a white-ish texture, so one 64x64 PNG covers every warhead.
 *
 * Model units are Minecraft pixels (16 = one block). The missile is 40 px long (~2.5 blocks), which is
 * the ICBM-mod silhouette: long, thin, finned, with a visible plume at the tail.
 */
public class MissileRenderer extends EntityRenderer<MissileEntity> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "textures/entity/missile.png");

    /** Model pixels per block. */
    private static final float SCALE = 1.0F / 16.0F;
    /** How much bigger than "life size" the missile is drawn. ICBM silhouettes want to be seen. */
    private static final float BULK = 1.7F;

    private final ModelPart booster;
    private final ModelPart tube;
    private final ModelPart band;
    private final ModelPart band2;
    private final ModelPart noseTip;
    private final ModelPart fins;
    private final ModelPart plume;

    public MissileRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F;

        // A LayerDefinition is the standard way to build a model in code (it is what every vanilla mob
        // does). Parts are named so the renderer can tint them separately: the body stays painted metal
        // while the nose, band and fins take the warhead's colour.
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Built nose-up in model space: y=-22 is the tip, y=+20 is the exhaust plume. The renderer
        // rotates this "up" axis onto the missile's actual velocity, so the model never has to know
        // which way it is flying.
        root.addOrReplaceChild("tube", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 20, 8), PartPose.ZERO);
        root.addOrReplaceChild("band", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, -9.0F, -4.5F, 9, 3, 9), PartPose.ZERO);
        root.addOrReplaceChild("band2", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.5F, 6.0F, -4.5F, 9, 2, 9), PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-2.5F, -18.0F, -2.5F, 5, 6, 5)
                .texOffs(32, 12).addBox(-1.5F, -22.0F, -1.5F, 3, 4, 3)
                .texOffs(32, 20).addBox(-3.5F, -12.0F, -3.5F, 7, 4, 7), PartPose.ZERO);
        root.addOrReplaceChild("booster", CubeListBuilder.create()
                .texOffs(0, 28).addBox(-5.0F, 12.0F, -5.0F, 10, 4, 10), PartPose.ZERO);
        root.addOrReplaceChild("fins", CubeListBuilder.create()
                // four tail fins
                .texOffs(40, 28).addBox(-9.0F, 8.0F, -0.5F, 5, 8, 1)
                .texOffs(40, 28).addBox(4.0F, 8.0F, -0.5F, 5, 8, 1)
                .texOffs(40, 28).addBox(-0.5F, 8.0F, -9.0F, 1, 8, 5)
                .texOffs(40, 28).addBox(-0.5F, 8.0F, 4.0F, 1, 8, 5)
                // four canards near the nose
                .texOffs(52, 28).addBox(-6.0F, -8.0F, -0.5F, 2, 4, 1)
                .texOffs(52, 28).addBox(4.0F, -8.0F, -0.5F, 2, 4, 1)
                .texOffs(52, 28).addBox(-0.5F, -8.0F, -6.0F, 1, 4, 2)
                .texOffs(52, 28).addBox(-0.5F, -8.0F, 4.0F, 1, 4, 2), PartPose.ZERO);
        root.addOrReplaceChild("plume", CubeListBuilder.create()
                .texOffs(0, 44).addBox(-3.5F, 16.0F, -3.5F, 7, 8, 7), PartPose.ZERO);

        ModelPart baked = LayerDefinition.create(mesh, 64, 64).bakeRoot();
        this.tube = baked.getChild("tube");
        this.band = baked.getChild("band");
        this.band2 = baked.getChild("band2");
        this.noseTip = baked.getChild("nose");
        this.booster = baked.getChild("booster");
        this.fins = baked.getChild("fins");
        this.plume = baked.getChild("plume");
    }

    @Override
    public ResourceLocation getTextureLocation(MissileEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(MissileEntity entity, float entityYaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        pose.pushPose();

        // A missile in a silo stands up. It is not travelling anywhere yet, so the velocity-based
        // orientation below would be undefined; give the docked case its own pose so a loaded pad reads
        // as a loaded pad from a hundred blocks away.
        boolean docked = entity.isDocked();

        // 1. point the model's "up" axis along the direction the missile is actually travelling.
        Vec3 direction = docked ? new Vec3(0.0D, 1.0D, 0.0D) : entity.travelDirection();
        Vector3f from = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f to = new Vector3f((float) direction.x, (float) direction.y, (float) direction.z);
        if (to.lengthSquared() < 1.0E-6F) {
            to.set(0.0F, 1.0F, 0.0F);
        }
        Quaternionf rotation = new Quaternionf().rotationTo(from, to.normalize());
        pose.mulPose(rotation);

        // 2. a lazy roll so a long flight does not look like a static prop. In the silo the roll is a
        // fraction of that: cold, waiting, just enough movement to show it is not a block.
        float roll = (entity.tickCount + partialTick) * (docked ? 0.2F : 1.6F);
        pose.mulPose(Axis.YP.rotationDegrees(roll));

        if (docked) {
            // Sink it into the pad's hatch so it reads as standing IN the silo, not on a plate. The boosters
            // are below the hatch line; only the body and nose clear the rim, which is what a real silo
            // looks like from outside.
            pose.translate(0.0F, -0.35F, 0.0F);
        }
        pose.scale(SCALE * BULK, SCALE * BULK, SCALE * BULK);
        pose.translate(0.0F, 4.0F, 0.0F);

        int tint = tintFor(entity.warheadOrdinal());
        VertexConsumer consumer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        int overlay = OverlayTexture.NO_OVERLAY;

        // 3. paint it: metal body, tinted warhead, blue-white plume.
        this.tube.render(pose, consumer, light, overlay, 0xFFFFFFFF);
        this.booster.render(pose, consumer, light, overlay, 0xFFB0B4BC);
        this.band.render(pose, consumer, light, overlay, tint);
        this.band2.render(pose, consumer, light, overlay, tint);
        this.noseTip.render(pose, consumer, light, overlay, tint);
        this.fins.render(pose, consumer, light, overlay, tint);

        // No plume in the tube: the motor is not lit.
        float throttle = docked ? 0.0F : Mth.clamp(entity.throttle(), 0.0F, 1.0F);
        if (throttle > 0.05F) {
            float flicker = 0.85F + 0.15F * Mth.sin((entity.tickCount + partialTick) * 1.9F);
            pose.pushPose();
            pose.translate(0.0F, 0.0F, 0.0F);
            pose.scale(1.0F, 0.6F + 0.9F * throttle * flicker, 1.0F);
            this.plume.render(pose, consumer, light, overlay, 0xFFFFD2A0);
            pose.popPose();
        }

        pose.popPose();
        super.render(entity, entityYaw, partialTick, pose, buffers, light);
    }

    /** The warhead's colour, as an ARGB int the model renderer multiplies the texture by. */
    public static int tintFor(int warheadOrdinal) {
        WarheadType[] values = WarheadType.values();
        WarheadType type = warheadOrdinal >= 0 && warheadOrdinal < values.length
                ? values[warheadOrdinal]
                : WarheadType.HE;
        return 0xFF000000 | switch (type) {
            case DECOY -> 0x9AA0A6;
            case EMP -> 0x66D9FF;
            case HE -> 0xFFC24A;
            case INCENDIARY -> 0xFF6A2B;
            case CLUSTER -> 0xC9E06B;
            case FISSION -> 0x8BFF9C;
            case FUSION -> 0xFFE96B;
            case ANTIMATTER -> 0xD07BFF;
            case NUMISMATIC -> 0xFFD34A;
            case BULWARK -> 0x7FB2FF;
            case CONTAGION -> 0x7CD46B;
            case SPELL -> 0x66FFE0;
            case CARGO -> 0xC79A5B;
            case MINEFIELD -> 0xE0A0A0;
            case TRACKING -> 0xFF8FBF;
            case CONSTRUCTOR -> 0x8FD6A0;
            case SPATIAL -> 0x7BC8FF;
            case CLUCKER -> 0xF7E07A;
            case VIOLATOR -> 0x3A3A4A;
            case BEEF -> 0xC98B6B;
            case DUD -> 0x9AA0A6;
            // Apex roster: one colour per weapon, matched to its shader and its particle palette.
            case WORLD_EATER -> 0x6E3FD0;
            case STELLAR_REQUIEM -> 0xFFF3B0;
            case REALITY_BREAKER -> 0xB14BFF;
            case SPACETIME_HAMMER -> 0x9FD6FF;
            case UNIVERSAL_IMPLODER -> 0x7A5CFF;
            case RAGNAROK -> 0xFF4A3D;
            case WORLD_RUNE -> 0x5CFFC8;
            case ABYSSAL_COVENANT -> 0x2A0B3D;
            case SOUL_FURNACE -> 0x8CFF6B;
            case ARCANE_SINGULARITY -> 0xC07BFF;
            case QUASAR_LANCE -> 0xFFFFFF;
            case STARFORGE -> 0xFFD25C;
            case BIG_BANG_SEED -> 0xFFF0FF;
            case REALITY_CORRUPTOR -> 0xFF2FD0;
            case ABSOLUTE_ZERO -> 0x0A0A12;
            case OMEGA -> 0xFF1F1F;
            case ABSOLUTE_ANNIHILATION -> 0xE8E8FF;
        };
    }
}
