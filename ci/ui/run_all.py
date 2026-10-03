# -*- coding: utf-8 -*-
import sys

import runner
import tests_a  # noqa: F401
import tests_b  # noqa: F401
import tests_c  # noqa: F401
import tests_d  # noqa: F401

if __name__ == "__main__":
    import driver
    shard = sys.argv[1] if len(sys.argv) > 1 else "a"
    driver.warmup()
    runner.run(shard)
    sys.exit(0)
