package com.thinklab.domain.valueobject;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Domain Value Object: Type-safe enumeration representing supported cryptographic hashing algorithms.
 *
 * <p><b>Architectural Role:</b>
 * This enumeration acts as the definitive, unified source of truth within the Domain Layer for
 * all cryptographic hashing capabilities. It abstracts the underlying Java Cryptography Architecture (JCA)
 * string identifiers into strongly-typed domain concepts.
 *
 * <p><b>Contractual Obligations:</b>
 * <ul>
 *   <li><b>Immutability:</b> All definitions and mappings are deeply immutable.</li>
 *   <li><b>Framework Agnosticism:</b> Contains zero infrastructure dependencies (e.g., Jackson or Spring annotations)
 *       to adhere strictly to Hexagonal Architecture principles.</li>
 *   <li><b>Fail-Fast Instantiation:</b> Throws unrecoverable exceptions if the required cryptographic
 *       algorithms are missing from the JVM's registered Security Providers.</li>
 * </ul>
 *
 * <p><b>Concurrency & Thread-Safety:</b>
 * The enumeration instances and their state are globally thread-safe. However, the {@link MessageDigest}
 * instances returned by {@link #getMessageDigest()} are stateful and <b>NOT thread-safe</b>.
 * A new instance is provisioned per invocation to guarantee thread confinement within reactive streams.
 *
 * @author ThinkLab
 * @since 1.0
 */
public enum HashAlgorithm {

    /**
     * SHA-256 cryptographic hash algorithm. Standard for general secure hashing and data integrity checks.
     */
    SHA_256("SHA-256"),

    /**
     * SHA-512 cryptographic hash algorithm. Utilized for high-security payloads and collision resistance.
     */
    SHA_512("SHA-512"),

    /**
     * SHA3-256 cryptographic hash algorithm. NIST standard (Keccak) offering a different internal
     * structure (sponge construction) to mitigate length-extension attacks.
     */
    SHA3_256("SHA3-256"),

    /**
     * SHA3-512 cryptographic hash algorithm. NIST standard (Keccak) optimized for maximum security.
     * <b>Mandatory standard for Identity Sovereignty base deterministic seeds.</b>
     */
    SHA3_512("SHA3-512"),

    /**
     * BLAKE3 (256-bit) cryptographic hash algorithm. Optimized for high-throughput, highly-parallelizable
     * hashing scenarios. <i>Requires BouncyCastle or equivalent security provider.</i>
     */
    BLAKE3_256("BLAKE3-256"),

    /**
     * SHA-1 cryptographic hash algorithm.
     *
     * @deprecated Vulnerable to theoretical and practical collision attacks (SHAttered).
     *             Retained strictly for legacy integration and verification of external payloads.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    SHA_1("SHA-1"),

    /**
     * MD5 cryptographic hash algorithm.
     *
     * @deprecated Cryptographically broken. Highly vulnerable to collision attacks.
     *             Retained strictly for legacy backward compatibility.
     */
    @Deprecated(since = "1.0", forRemoval = false)
    MD5("MD5");

    private static final Map<String, HashAlgorithm> BY_STANDARD_NAME;
    private final String standardName;

    static {
        BY_STANDARD_NAME = Stream.of(values())
                .collect(Collectors.toUnmodifiableMap(
                        alg -> alg.standardName.toUpperCase(),
                        alg -> alg
                ));
    }

    /**
     * Constructs a {@link HashAlgorithm} with its corresponding JCA standard name.
     *
     * @param standardName The algorithm identifier registered within JCA.
     */
    HashAlgorithm(String standardName) {
        this.standardName = standardName;
    }

    /**
     * Factory method for resolving an algorithm based on its standard name.
     *
     * <p><b>Contract:</b> Case-insensitive resolution. Defensively rejects null inputs.
     *
     * @param name The algorithm standard name (e.g., "SHA-256").
     * @return The corresponding {@link HashAlgorithm}.
     * @throws NullPointerException if the provided name is null.
     * @throws IllegalArgumentException if the provided name does not match any supported algorithm.
     */
    public static HashAlgorithm fromStandardName(String name) {
        Objects.requireNonNull(name, "HashAlgorithm standard name must not be null.");

        HashAlgorithm alg = BY_STANDARD_NAME.get(name.toUpperCase());
        if (alg == null) {
            throw new IllegalArgumentException("Unsupported cryptographic algorithm identifier: '" + name + "'");
        }
        return alg;
    }

    /**
     * Retrieves the standard algorithm identifier.
     *
     * @return The JCA-compliant identifier string (e.g., "SHA3-512").
     */
    public String getStandardName() {
        return standardName;
    }

    /**
     * Obtains a freshly initialized {@link MessageDigest} instance for the chosen algorithm.
     *
     * <p><b>Reactive Concurrency Note:</b>
     * This method provisions a <i>new</i> digest instance per invocation because JCA digests are stateful.
     * Within Project Reactor flows, ensure this method is called within a {@code defer()} or {@code map()}
     * operator to guarantee the instance remains confined to the executing thread.
     *
     * @return A newly initialized {@link MessageDigest}.
     * @throws IllegalStateException if the algorithm is unavailable in the current JVM Security Providers
     *                               (Critical Infrastructure Failure).
     */
    public MessageDigest getMessageDigest() {
        return digestFor(this.standardName);
    }

    /** Resolves a digest by JCA name, reporting a missing provider as an infrastructure failure. */
    public static MessageDigest digestFor(String standardName) {
        try {
            return MessageDigest.getInstance(standardName);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(
                    "Critical Infrastructure Failure: Cryptographic algorithm '" + standardName +
                            "' is not supported by the currently registered JVM Security Providers.", e);
        }
    }
}