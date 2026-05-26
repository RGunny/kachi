import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Random;
import java.util.UUID;

public class GenerateDataset {

    private static final String[] SERVICES = {"user", "keyword", "notification", "history"};
    private static final Instant BASE_TIME = Instant.parse("2026-05-21T00:00:00Z");
    private static final DateTimeFormatter MYSQL_DATETIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")
            .withZone(ZoneOffset.UTC);

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        new GenerateDataset().generate(arguments.rows, Path.of(arguments.out));
    }

    private void generate(int rows, Path out) throws IOException {
        Files.createDirectories(out);
        Random random = new Random(20260521L);

        try (
                BufferedWriter bigint = Files.newBufferedWriter(out.resolve("bigint.csv"));
                BufferedWriter bin16 = Files.newBufferedWriter(out.resolve("bin16_uuid_v7.csv"));
                BufferedWriter char36 = Files.newBufferedWriter(out.resolve("char36_uuid_v7.csv"));
                BufferedWriter prefix = Files.newBufferedWriter(out.resolve("prefix_id.csv"))
        ) {
            for (int index = 0; index < rows; index++) {
                String service = SERVICES[index % SERVICES.length];
                UUID id = uuidV7(index, random);
                String uuid = id.toString();
                String hex = uuidToHex(id);
                String createdAt = MYSQL_DATETIME.format(BASE_TIME.plusSeconds(index));
                String payload = "payload-" + index;

                bigint.write(csv(service, createdAt, payload));
                bin16.write(csv(hex, service, createdAt, payload));
                char36.write(csv(uuid, service, createdAt, payload));
                prefix.write(csv(service + "_" + uuid, service, createdAt, payload));
            }
        }

        System.out.println("Generated rows=" + rows + " into " + out.toAbsolutePath().normalize());
    }

    private UUID uuidV7(int index, Random random) {
        long timestampMillis = BASE_TIME.toEpochMilli() + index;
        long randomA = random.nextLong() & 0x0FFFL;
        long randomB = random.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;

        long mostSignificantBits = ((timestampMillis & 0xFFFF_FFFF_FFFFL) << 16)
                | (0x7L << 12)
                | randomA;
        long leastSignificantBits = (0b10L << 62) | randomB;

        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    private String uuidToHex(UUID uuid) {
        byte[] bytes = new byte[16];
        writeLong(bytes, 0, uuid.getMostSignificantBits());
        writeLong(bytes, 8, uuid.getLeastSignificantBits());
        return HexFormat.of().formatHex(bytes);
    }

    private void writeLong(byte[] target, int offset, long value) {
        for (int index = 7; index >= 0; index--) {
            target[offset + index] = (byte) value;
            value >>>= 8;
        }
    }

    private String csv(String... values) {
        return String.join(",", values) + "\n";
    }

    private record Arguments(int rows, String out) {
        private static Arguments parse(String[] args) {
            int rows = 100_000;
            String out = "generated";

            for (int index = 0; index < args.length; index++) {
                if ("--rows".equals(args[index])) {
                    rows = Integer.parseInt(args[++index]);
                    continue;
                }
                if ("--out".equals(args[index])) {
                    out = args[++index];
                    continue;
                }
                throw new IllegalArgumentException("Unknown argument: " + args[index]);
            }

            return new Arguments(rows, out);
        }
    }
}
