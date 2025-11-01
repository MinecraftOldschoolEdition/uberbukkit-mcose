package net.minecraft.server.registry;

import net.minecraft.server.util.ResourceLocation;

public final class SoundEventRegistryBootstrap {
    private SoundEventRegistryBootstrap() {}

    public static void initialize() {
        // Mirror the client mappings for parity (server can reference them by key)
        reg("ui.button.click", "random.click");
        reg("entity.item.pickup", "random.pop");
        reg("entity.arrow.shoot", "random.bow");
        reg("entity.arrow.hit", "random.drr");
        reg("block.door.open", "random.door_open");
        reg("block.door.close", "random.door_close");
        reg("entity.generic.explode", "random.explode");
        reg("entity.generic.splash", "random.splash");
        reg("block.fire.extinguish", "random.fizz");
        reg("block.glass.break", "random.glass");
        reg("block.fire.ambient", "fire.fire");
        reg("ambient.cave", "ambient.cave.cave");
        reg("weather.rain", "ambient.weather.rain");
        reg("weather.thunder", "ambient.weather.thunder");
        reg("block.water.ambient", "liquid.water");
        reg("block.lava.ambient", "liquid.lava");
        reg("block.lava.pop", "liquid.lavapop");
        reg("block.portal.trigger", "portal.trigger");
        reg("block.portal.travel", "portal.travel");
        reg("block.portal.ambient", "portal.portal");
        reg("entity.player.hurt", "random.hurt");
        reg("entity.player.death", "random.hurt");
        reg("entity.player.breath", "random.breath");
        reg("entity.player.burp", "random.burp");
        reg("entity.player.drink", "random.drink");
        reg("entity.player.eat", "random.eat");
        reg("entity.player.levelup", "random.levelup");
        reg("entity.experience_orb.pickup", "random.orb");
        reg("entity.creeper.hurt", "mob.creeper");
        reg("entity.creeper.death", "mob.creeperdeath");
        reg("entity.creeper.primed", "random.fuse");
        reg("entity.slime.ambient", "mob.slime");
        reg("entity.slime.attack", "mob.slimeattack");
        reg("entity.ghast.ambient", "mob.ghast.moan");
        reg("entity.ghast.hurt", "mob.ghast.scream");
        reg("entity.ghast.death", "mob.ghast.death");
        reg("entity.ghast.charge", "mob.ghast.charge");
        reg("entity.ghast.shoot", "mob.ghast.fireball");
        reg("entity.pig_zombie.ambient", "mob.zombiepig.zpig");
        reg("entity.pig_zombie.hurt", "mob.zombiepig.zpighurt");
        reg("entity.pig_zombie.death", "mob.zombiepig.zpigdeath");
        reg("entity.pig_zombie.angry", "mob.zombiepig.zpigangry");
        reg("entity.sheep.ambient", "mob.sheep");
        reg("entity.sheep.hurt", "mob.sheep");
        reg("entity.sheep.death", "mob.sheep");
        reg("entity.pig.ambient", "mob.pig");
        reg("entity.pig.death", "mob.pigdeath");
        reg("entity.cow.ambient", "mob.cow");
        reg("entity.cow.hurt", "mob.cowhurt");
        reg("entity.cow.death", "mob.cowhurt");
        reg("entity.chicken.ambient", "mob.chicken");
        reg("entity.chicken.hurt", "mob.chickenhurt");
        reg("entity.chicken.death", "mob.chickenhurt");
        reg("entity.chicken.egg", "mob.chickenplop");
        reg("entity.skeleton.ambient", "mob.skeleton");
        reg("entity.skeleton.hurt", "mob.skeletonhurt");
        reg("entity.skeleton.death", "mob.skeletonhurt");
        reg("entity.spider.ambient", "mob.spider");
        reg("entity.spider.hurt", "mob.spider");
        reg("entity.spider.death", "mob.spiderdeath");
        reg("entity.zombie.ambient", "mob.zombie");
        reg("entity.zombie.hurt", "mob.zombiehurt");
        reg("entity.zombie.death", "mob.zombiedeath");
        reg("entity.enderman.ambient", "mob.endermen.idle");
        reg("entity.enderman.hurt", "mob.endermen.hit");
        reg("entity.enderman.death", "mob.endermen.death");
        reg("entity.enderman.portal", "mob.endermen.portal");
        reg("entity.wolf.bark", "mob.wolf.bark");
        reg("entity.wolf.hurt", "mob.wolf.hurt");
        reg("entity.wolf.death", "mob.wolf.death");
        reg("entity.wolf.whine", "mob.wolf.whine");
        reg("entity.wolf.pant", "mob.wolf.panting");
        reg("entity.wolf.shake", "mob.wolf.shake");
        reg("block.note_block.harp", "note.harp");
        reg("block.note_block.bass", "note.bass");
        reg("block.note_block.snare", "note.snare");
        reg("block.note_block.bd", "note.bd");
        reg("block.note_block.hat", "note.hat");
        reg("block.note_block.pling", "note.pling");
        reg("block.note_block.bassattack", "note.bassattack");
        reg("block.step.stone", "step.stone");
        reg("block.step.wood", "step.wood");
        reg("block.step.gravel", "step.gravel");
        reg("block.step.grass", "step.grass");
        reg("block.step.sand", "step.sand");
        reg("block.step.snow", "step.snow");
        reg("block.step.cloth", "step.cloth");
        reg("block.step.ladder", "step.ladder");
        reg("block.dig.stone", "dig.stone");
        reg("block.dig.wood", "dig.wood");
        reg("block.dig.gravel", "dig.gravel");
        reg("block.dig.grass", "dig.grass");
        reg("block.dig.sand", "dig.sand");
        reg("block.dig.snow", "dig.snow");
        reg("block.dig.cloth", "dig.cloth");
        reg("music_disc.13", "records.13");
        reg("music_disc.cat", "records.cat");
        reg("music_disc.blocks", "records.blocks");
        reg("music_disc.chirp", "records.chirp");
        reg("music_disc.far", "records.far");
        reg("music_disc.mall", "records.mall");
        reg("music_disc.mellohi", "records.mellohi");
        reg("music_disc.stal", "records.stal");
        reg("music_disc.strad", "records.strad");
        reg("music_disc.ward", "records.ward");
        reg("music_disc.11", "records.11");
        reg("music_disc.wait", "records.wait");
        // Custom discs present in assets/streaming
        reg("music_disc.aria_math", "records.Aria Math");
        reg("music_disc.dog", "records.Dog");
        reg("music_disc.certitudes", "records.certitudes");
        reg("music_disc.tsuki_no_koibumi", "records.tsuki no koibumi");
        reg("music_disc.where_are_we_now", "records.where are we now");
        reg("block.piston.extend", "tile.piston.out");
        reg("block.piston.contract", "tile.piston.in");
        reg("block.chest.open", "random.chestopen");
        reg("block.chest.close", "random.chestclosed");
        reg("entity.fishing_bobber.splash", "random.splash");
        reg("entity.arrow.hit_old", "random.bowhit");
        reg("entity.generic.explode_old", "random.old_explode");
        reg("entity.minecart.riding", "vehicle.minecart");
        // Blaze
        reg("entity.blaze.ambient", "mob.blaze.breathe");
        reg("entity.blaze.hurt", "mob.blaze.hit");
        reg("entity.blaze.death", "mob.blaze.death");
        // Ocelot/Cat
        reg("entity.cat.hiss", "mob.cat.hiss");
        reg("entity.cat.hurt", "mob.cat.hitt");
        reg("entity.cat.meow", "mob.cat.meow");
        reg("entity.cat.purr", "mob.cat.purr");
        reg("entity.cat.purreow", "mob.cat.purreow");
        // Magma Cube
        reg("entity.magma_cube.ambient", "mob.magmacube.small");
        reg("entity.magma_cube.hurt", "mob.magmacube.hit");
        reg("entity.magma_cube.death", "mob.magmacube.death");
        reg("entity.magma_cube.jump", "mob.magmacube.jump");
        // Silverfish
        reg("entity.silverfish.ambient", "mob.silverfish.say");
        reg("entity.silverfish.hurt", "mob.silverfish.hit");
        reg("entity.silverfish.death", "mob.silverfish.kill");
        // Iron Golem
        reg("entity.iron_golem.ambient", "mob.irongolem.walk");
        reg("entity.iron_golem.hurt", "mob.irongolem.hit");
        reg("entity.iron_golem.death", "mob.irongolem.death");
        reg("entity.item.break", "random.break");
        reg("block.wood.click", "random.wood click");
    }

    private static void reg(String modernKey, String legacy) {
        try {
            Registries.SOUND_EVENT.registerIfAbsent(new ResourceLocation("minecraft", modernKey), legacy);
        } catch (Throwable ignored) {}
    }
}


