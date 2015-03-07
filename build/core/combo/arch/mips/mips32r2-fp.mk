# Configuration for Android on MIPS.
# Generating binaries for MIPS32R2/hard-float/little-endian

ARCH_MIPS_HAS_FPU	:=true
ARCH_HAVE_ALIGNED_DOUBLES :=true
arch_variant_cflags := \
    -EL \
    -march=mips32r2 \
    -mtune=mips32r2 \
    -mips32r2 \
    -mhard-float \
    -msynci

arch_variant_ldflags := \
    -EL
