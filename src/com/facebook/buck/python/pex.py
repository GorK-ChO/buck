#!/usr/bin/env python

from __future__ import print_function
import os
import sys
import json
import shutil
import tempfile
import optparse

# Find the buck repo root relative this file.  We'll use this
# to find the PEX library components and a recent version of
# setuptools, all hosted in buck's third-party dir.
BUCK_ROOT = os.sep.join(__file__.split(os.sep)[:-6])


# Add the path to the PEX library code, and the version of setuptools
# it requires, to our path and import the key parts we need.
sys.path.insert(0, os.path.join(
    BUCK_ROOT,
    'third-party/py/twitter-commons/src/python'))
sys.path.insert(0, os.path.join(BUCK_ROOT, 'third-party/py/setuptools'))
from twitter.common.python.pex_builder import PEXBuilder
from twitter.common.python.interpreter import PythonInterpreter


def dereference_symlinks(src):
    """
    Resolve all symbolic references that `src` points to.  Note that this
    is different than `os.path.realpath` as path components leading up to
    the final location may still be symbolic links.
    """

    while os.path.islink(src):
        src = os.path.join(os.path.dirname(src), os.readlink(src))

    return src


def main():
    parser = optparse.OptionParser(usage="usage: %prog [options] output")
    parser.add_option('--entry-point', default='__main__')
    parser.add_option('--python', default=sys.executable)
    options, args = parser.parse_args()
    if len(args) == 1:
        output = args[0]
    else:
        parser.error("'output' positional argument is required")
        return 1

    # The manifest is passed via stdin, as it can sometimes get too large
    # to be passed as a CLA.
    manifest = json.load(sys.stdin)

    # Setup a temp dir that the PEX builder will use as its scratch dir.
    tmp_dir = tempfile.mkdtemp()
    try:

        # The version of pkg_resources.py (from setuptools) on some distros is
        # too old for PEX.  So we keep a recent version in the buck repo and
        # force it into the process by constructing a custom PythonInterpreter
        # instance using it.
        interpreter = PythonInterpreter(
            options.python,
            PythonInterpreter.from_binary(options.python).identity,
            extras={('setuptools', '1.0'):
                    os.path.join(BUCK_ROOT, 'third-party/py/setuptools')})

        pex_builder = PEXBuilder(
            path=tmp_dir,
            interpreter=interpreter,
        )

        # Mark this PEX as zip-safe, meaning everything will stayed zipped up
        # and we'll rely on python's zip-import mechanism to load modules from
        # the PEX.  This may not work in some situations (e.g. native
        # libraries, libraries that want to find resources via the FS), so
        # we'll want to revisit this.
        pex_builder.info.zip_safe = True

        # Set the starting point for this PEX.
        pex_builder.info.entry_point = options.entry_point

        # Add the sources listed in the manifest.
        for dst, src in manifest['modules'].iteritems():
            # NOTE(agallagher): calls the `add_source` and `add_resource` below
            # hard-link the given source into the PEX temp dir.  Since OS X and
            # Linux behave different when hard-linking a source that is a
            # symbolic link (Linux does *not* follow symlinks), resolve any
            # layers of symlinks here to get consistent behavior.
            pex_builder.add_source(dereference_symlinks(src), dst)

        # Add resources listed in the manifest.
        for dst, src in manifest['resources'].iteritems():
            # NOTE(agallagher): see rationale above.
            pex_builder.add_resource(dereference_symlinks(src), dst)

        # Generate the PEX file.
        pex_builder.build(output)

    # Always try cleaning up the scratch dir, ignoring failures.
    finally:
        shutil.rmtree(tmp_dir, True)


sys.exit(main())
