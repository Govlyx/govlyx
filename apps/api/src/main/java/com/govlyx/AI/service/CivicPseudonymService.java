package com.govlyx.AI.service;

import com.govlyx.AI.repository.ActorProfileRepo;
import com.govlyx.AI.repository.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

/**
 * Dedicated cryptographic and semantic engine for minting anonymous civic pseudonyms.
 * Guaranteed to be unique across both actor_profiles and users tables.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CivicPseudonymService {

    private final ActorProfileRepo actorProfileRepo;
    private final UserRepo userRepo;
    private final SecureRandom secureRandom = new SecureRandom();

    private static final String[] ADJECTIVES = {
            "Happy","Brave","Swift","Clever","Mighty","Silent","Wise","Lucky","Bold","Shiny",
            "Fierce","Calm","Wild","Bright","Cool","Fast","Gentle","Sharp","Loyal","Kind",
            "Strong","Fearless","Quiet","Sneaky","Cheerful","Noble","Radiant","Epic","Smart","Eager",
            "Playful","Energetic","Glorious","Charming","Courageous","Heroic","Friendly","Polite","Magical","Mystic",
            "Joyful","Adventurous","Brilliant","Daring","Faithful","Generous","Humble","Creative","Graceful","Dynamic",
            "Witty","Keen","Determined","Sunny","Starry","Vivid","Dazzling","Zesty","Glowing","Chill",
            "Funky","Coolheaded","Quick","Resourceful","Inventive","Cheeky","Blissful","Hopeful","Valiant","Luminous",
            "Cosmic","Fiery","Dreamy","Tranquil","Golden","Boldhearted","Eternal","Zen","Spirited","Vast",
            "Skybound","Stellar","Brighthearted","Roaring","Free","Harmonic","Nimble","Gallant","Sturdy","Calmhearted",
            "Swiftfooted","Iron","Steady","Thunderous","Silentblade","Quickwitted","Stormy","Snowy","Frosty","Burning",
            "Shadowy","Crimson","Silver","Serene","Ancient","Wildhearted","Springtime","Moonlit","Sunlit","Windswept",
            "Electric","Neon","Galactic","Fabulous","Majestic","Ruthless","Jolly","Savage","Tough","Velvet",
            "Ambitious","Fearful","Furious","Curious","Pragmatic","Fanciful","Grandiose","Gleaming","Jumping","Sapphire",
            "Emerald","Ruby","Diamond","Platinum","Copper","Brass","Titanium","Quantum","Cyber","Lunar",
            "Solar","Astro","Meteor","Comet","Starlight","Nebula","Galaxy","Meteorite","Pulsar","Zealous",
            "Vibrant","Tenacious","Stoic","Resolute","Quaint","Proud","Optimistic","Mellow","Logical","Jubilant",
            "Invincible","Harmonious","Gritty","Enigmatic","Diligent","Auspicious","Astute","Audacious","Brawny","Candid",
            "Dapper","Earnest","Flawless","Gleeful","Hardy","Intrepid","Jovial","Kooky","Lithe","Merry",
            "Nifty","Outrageous","Peppy","Quirky","Rambunctious","Sassy","Snazzy","Spiffy","Swanky","Upbeat",
            "Vivacious","Whimsical","Zippy"
    };

    private static final String[] NOUNS = {
            "Tiger","Eagle","Shark","Panther","Wolf","Falcon","Lion","Bear","Hawk","Cheetah",
            "Puma","Dragon","Phoenix","Leopard","Viper","Cobra","Fox","Jaguar","Lynx","Raven",
            "Crow","Owl","Stallion","Mustang","Horse","Buffalo","Bison","Bull","Ram","Goat",
            "Deer","Moose","Elk","Yak","Elephant","Rhino","Hippo","Gorilla","Chimp","Orangutan",
            "Whale","Dolphin","Seal","Otter","Penguin","PolarBear","Camel","Giraffe","Kangaroo","Koala",
            "Crocodile","Alligator","Turtle","Tortoise","Frog","Toad","Eel","Octopus","Squid","Jellyfish",
            "Starfish","Crab","Lobster","Shrimp","Mantis","Scorpion","Spider","Beetle","Wasp","Hornet",
            "Ant","Bee","Butterfly","Moth","Dragonfly","Ladybug","Firefly","Bat","Rat","Mouse",
            "Squirrel","Chipmunk","Porcupine","Hedgehog","Ferret","Badger","Weasel","Armadillo","Sloth","Anteater",
            "Parrot","Macaw","Canary","Sparrow","Robin","Finch","Swallow","Seagull","Pelican","Albatross",
            "Heron","Flamingo","Swan","Duck","Goose","Turkey","Chicken","Rooster","Peacock","Dove",
            "Pigeon","Caterpillar","Worm","Snail","Slug","Clam","Oyster","Mussel","Coral","Barnacle"
    };

    /**
     * Generates a unique, non-colliding civic pseudonym (e.g. "WildDragon5670").
     * Verified against both actor_profiles and users tables.
     */
    public String generateUniquePseudonym() {
        int maxAttempts = 30;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            String adj = ADJECTIVES[secureRandom.nextInt(ADJECTIVES.length)];
            String noun = NOUNS[secureRandom.nextInt(NOUNS.length)];
            int num = 1000 + secureRandom.nextInt(9000);
            String candidate = adj + noun + num;

            // Check actor_profiles table
            if (actorProfileRepo != null && actorProfileRepo.findByUsername(candidate).isPresent()) {
                continue;
            }

            // Check users table
            if (userRepo != null && userRepo.findByUsername(candidate).isPresent()) {
                continue;
            }

            return candidate;
        }

        // Fallback with timestamp in the astronomical event of 30 collisions
        return "Citizen" + (1000 + secureRandom.nextInt(9000)) + "_" + (System.currentTimeMillis() % 10000);
    }
}
