---
layout: page
title: Community Members
description: Project Community Page
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

<br/><br/><br/>

### {{ site.data.project.short_name }} Team Members

{% if site.data.contributors %}
<table class="table table-hover">
    <tr>
        <th><b></b></th><th><b>Full Name</b></th><th><b>Apache ID</b></th><th><b>GitHub</b><th><b>Role</b></th><th><b>Affiliation</b></th>
    </tr>
    {% for member in site.data.contributors %}
        <tr>
        <td><a href="http://github.com/{{ member.githubId }}"><img width="64" src="{% unless c.avatar %}http://github.com/{{ member.githubId }}.png{% else %}{{ member.avatar }}{% endunless %}"></a></td>
        <td>{{member.name}}</td>
        <td>{{member.apacheId}}</td>
        <td><a href="http://github.com/{{ member.githubId }}">{{ member.githubId }}</a></td>
        <td>{{member.role}}</td>
        <td>{{member.org}}</td>
        </tr>
    {% endfor %}
</table>
{% endif %}

