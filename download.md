---
layout: page
title: Downloads
description: Project Downloads page
group: nav-right
---
<!--
{% comment %}
Licensed to the Apache Software Foundation (ASF) under one or more
contributor license agreements.  See the NOTICE file distributed with
this work for additional information regarding copyright ownership.
The ASF licenses this file to you under the Apache License, Version 2.0
(the "License"); you may not use this file except in compliance with
the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
{% endcomment %}
-->
{% include JB/setup %}

## {{ site.data.project.name }} Downloads

{{ site.data.project.name }} is released as a source artifact, and also through Maven.

## Source releases

Release          | Date       | Commit   | Download
:--------------- | :--------- | :------- | :-------
{% for post in site.categories.release %}{% comment %}
{% endcomment %}{% if post.fullVersion %}{% comment %}
{% endcomment %}{% assign v = post.fullVersion %}{% comment %}
{% endcomment %}{% else %}{% comment %}
{% endcomment %}{% capture v %}{{ site.data.project.unix_name }}-{{ post.version }}{% endcapture %}{% comment %}
{% endcomment %}{% endif %}{% comment %}
{% endcomment %}{% if forloop.index0 < 2 %}{% comment %}
{% endcomment %}{% capture p %}http://www.apache.org/dyn/closer.lua?filename={{ site.data.project.incubator_slash_name }}/{{ v }}{% endcapture %}{% comment %}
{% endcomment %}{% assign q = "&action=download" %}{% comment %}
{% endcomment %}{% assign d = "https://www.apache.org/dist" %}{% comment %}
{% endcomment %}{% else %}{% comment %}
{% endcomment %}{% capture p %}http://archive.apache.org/dist/incubator/{{ site.data.project.unix_name }}/{{ v }}{% endcapture %}{% comment %}
{% endcomment %}{% assign q = "" %}{% comment %}
{% endcomment %}{% assign d = "https://archive.apache.org/dist/incubator" %}{% comment %}
{% endcomment %}{% endif %}{% comment %}
{% endcomment %}{{ post.version }}{% comment %}
{% endcomment %}  | {{ post.date | date_to_string }}{% comment %}
{% endcomment %}  | <a href="https://github.com/apache/{{ site.data.project.incubator_name }}/commit/{{ post.sha }}">{{ post.sha }}</a>{% comment %}
{% endcomment %}  | <a href="{{ p }}/{{ v }}-source-release.zip{{ q }}">zip</a>{% comment %}
{% endcomment %}  (<a href="{{ d }}/{{ site.data.project.incubator_slash_name }}/{{ v }}/{{ v }}-source-release.zip.md5">md5</a>{% comment %}
{% endcomment %} <a href="{{ d }}/{{ site.data.project.incubator_slash_name }}/{{ v }}/{{ v }}-source-release.zip.asc">pgp</a>){% comment %}
{% endcomment %}
{% endfor %}

### Verification

Choose a source distribution in either *tar* or *zip* format,
and [verify](http://www.apache.org/dyn/closer.cgi#verify)
using the corresponding *pgp* signature (using the committer file in
[KEYS](http://www.apache.org/dist/{{ site.data.project.unix_name }}/KEYS)).
If you cannot do that, the *md5* hash file may be used to check that the
download has completed OK.

For fast downloads, current source distributions are hosted on mirror servers;
older source distributions are in the
[archive](http://archive.apache.org/dist/incubator/{{ site.data.project.unix_name }}/).
If a download from a mirror fails, retry, and the second download will likely
succeed.

For security, hash and signature files are always hosted at
[Apache](https://www.apache.org/dist).

## Maven Central

{% highlight xml %}
<dependency>
  <groupId>org.apache.gossip</groupId>
  <artifactId>gossip</gossip>
  <version>...</version>
</dependency>
{% endhighlight %}
