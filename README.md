# Pallet crate for java

This a crate to install and run java via [Pallet](http://pallet.github.com/pallet).

[Release Notes](ReleaseNotes.md)

## Server Spec

The java crate defines the `java` function, that takes a settings map and
returns a server-spec for installing java.

## Settings

The java crate uses the following settings:

:vendor
one of #{:openjdk :oracle :sun}

:components
a set of #{:jdk :jre}

:strategy
allows override of the install strategy (:packages, :package-source, :rpm
or :debs)

:version
specify the java version to install

:packages
the packages that are used to install

:package-source
a non-default package source for the packages

:rpm
takes a map of remote-file options specifying a self-extracting rpm file
to install

:debs
takes a map of remote-directory options specifying an archive of deb files to
install. The archive should have no top level directory.

## Support

[On the group](http://groups.google.com/group/pallet-clj), or
[#pallet](http://webchat.freenode.net/?channels=#pallet) on freenode irc.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

Copyright 2010, 2011, 2012 Hugo Duncan.
