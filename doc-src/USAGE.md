## Server Spec

The java crate defines the `server-spec` function, that takes a settings map and
returns a server-spec for installing java.  You can use this in a `group-spec`
or `server-spec`.

```clj
(group-spec "my-node-with-java"
  :extends [(pallet.crate.java/server-spec {})])
```

The default server-spec uses the `:settings` and `:install` phase, so
remember to add the `:install` phase when lifting or converging a node
including this crate.

## Settings

The java crate uses the following settings:

`:vendor`
one of `#{:openjdk :oracle :sun :zulu}`

`:components`
a set of `#{:jdk :jre}`

`:strategy`
allows override of the install strategy (`:packages`, `:package-source`, `:rpm`
or `:debs`)

`:version`
specify the java version to install

`:packages`
the packages that are used to install

`:package-source`
a non-default package source for the packages

`:rpm`
takes a map of remote-file options specifying a self-extracting rpm file
to install

`:debs`
takes a map of remote-directory options specifying an archive of deb files to
install. The archive should have no top level directory.

`:local-file`, `:url`, etc
takes the location of a tar file containing a java binary distribution
