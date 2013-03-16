[Repository](https://github.com/pallet/java-crate) &#xb7;
[Issues](https://github.com/pallet/java-crate/issues) &#xb7;
[API docs](http://palletops.com/java-crate/0.8/api) &#xb7;
[Annotated source](http://palletops.com/java-crate/0.8/annotated/uberdoc.html) &#xb7;
[Release Notes](https://github.com/pallet/java-crate/blob/develop/ReleaseNotes.md)

Install and configure java.

### Dependency Information

```clj
:dependencies [[com.palletops/java-crate "0.8.0-beta.2"]]
```

### Releases

<table>
<thead>
  <tr><th>Pallet</th><th>Crate Version</th><th>Repo</th><th>GroupId</th></tr>
</thead>
<tbody>
  <tr>
    <th>0.8.0-beta.5</th>
    <td>0.8.0-beta.2</td>
    <td>clojars</td>
    <td>com.palletops</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-beta.2/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-beta.2/'>Source</a></td>
  </tr>
  <tr>
    <th>0.7</th>
    <td>0.7.1</td>
    <td>sonatype</td>
    <td>org.cloudhoist</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.7.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.7.1/'>Source</a></td>
  </tr>
  <tr>
    <th>0.6</th>
    <td>0.5.1</td>
    <td>sonatype</td>
    <td>org.cloudhoist</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.5.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.5.1/'>Source</a></td>
  </tr>
  <tr>
    <th>0.5</th>
    <td>0.5.1</td>
    <td>sonatype</td>
    <td>org.cloudhoist</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.5.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.5.1/'>Source</a></td>
  </tr>
  <tr>
    <th>0.8-alpha.7</th>
    <td>0.8.0-alpha.1</td>
    <td>sonatype</td>
    <td>org.cloudhoist</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-alpha.1/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-alpha.1/'>Source</a></td>
  </tr>
  <tr>
    <th>0.8-alpha.8</th>
    <td>0.8.0-alpha.2</td>
    <td>sonatype</td>
    <td>org.cloudhoist</td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-alpha.2/ReleaseNotes.md'>Release Notes</a></td>
    <td><a href='https://github.com/pallet/java-crate/blob/java-0.8.0-alpha.2/'>Source</a></td>
  </tr>
</tbody>
</table>

## Server Spec

The java crate defines the `server-spec` function, that takes a settings map and
returns a server-spec for installing java.  You can use this in a `group-spec`
or `server-spec`.

```clj
(group-spec "my-node-with-java"
  :extends [(pallet.crate.java/server-spec {})])
```

## Settings

The java crate uses the following settings:

`:vendor`
one of `#{:openjdk :oracle :sun}`

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

## Support

[On the group](http://groups.google.com/group/pallet-clj), or
[#pallet](http://webchat.freenode.net/?channels=#pallet) on freenode irc.

## License

Licensed under [EPL](http://www.eclipse.org/legal/epl-v10.html)

Copyright 2010, 2011, 2012, 2013 Hugo Duncan.
