# source this file, do not execute!
here=`pwd`

toml_version() {
    path="."
    if test -n "$1"; then path="$1"; fi
    grep version "$path"/pyproject.toml | sed 's/version *= *"\([^"]*\)".*/\1/'
}

pom_version() {
    if test -n "$1"; then cd "$1"; fi
    # There are deprecation warnings under the hood!
    mvn help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null
}

if realpath . > /dev/null 2>&1 ; then
    :
else
    # does not exist on Mac OSX
    realpath() {
        target=$1

        cd `dirname $target`
        target=`basename $target`

        # Iterate down a (possible) chain of symlinks
        while [ -L "$target" ]
        do
            target=`readlink $target`
            cd `dirname $target`
            target=`basename $target`
        done

        # Compute the canonicalized name by finding the physical path
        # for the directory we're in and appending the target file.
        phys_dir=`pwd -P`
        echo "$phys_dir/$target"
    }
fi
script_dir=$(dirname $(realpath $0))
