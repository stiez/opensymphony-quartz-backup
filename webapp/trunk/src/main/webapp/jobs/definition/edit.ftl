<html>
<head>
<script language="JavaScript">
function doAddParameter() {
	var name = document.getElementById("parameter.name");
	var desc = document.getElementById("parameter.description");
	var required=document.getElementById("parameter.required");

	if (name.value.length > 0) {
		addParameter(name.value, desc.value, required.checked);
		name.value="";
		desc.value="";
		required.value=1;
		required.checked=true;
	} else {
		alert('<@s.text name="error.paramname.null"/>');
	}
}

function addParameter(name, desc, required) {
	// alert('about to add parameter:' + name + ' ' + desc + ' ' + required);
	
	var parent = document.getElementById("paramterHolder");
	var proto = document.getElementById("proto");
		clone = proto.cloneNode(true);
		clone.setAttribute("id", "notproto");
		clone.getElementsByTagName("input")[0].value = name; //set the value for the dnsServer
		clone.getElementsByTagName("input")[0].name = "paramMap['" + name + "'].name"; 
		
		clone.getElementsByTagName("input")[1].value = desc; //set the value for the dnsServer
		clone.getElementsByTagName("input")[1].name = "paramMap['" + name + "'].description"; 
		
		clone.getElementsByTagName("input")[2].value = required; //set the value for the dnsServer
		clone.getElementsByTagName("input")[2].name = "paramMap['" + name + "'].required"; 

		clone.getElementsByTagName("td")[0].innerHTML = clone.getElementsByTagName("td")[0].innerHTML + ' ' + name; //set the value for the dnsServer
		clone.getElementsByTagName("td")[1].innerHTML = desc; //set the value for the dnsServer
		
		if (required) {
			reqStr='true';
		} else {
			reqStr='false';
		}
		clone.getElementsByTagName("td")[2].innerHTML = reqStr + " " + clone.getElementsByTagName("td")[2].innerHTML; 
	parent.appendChild(clone);

}

function removeParameter(obj) {
	deleteByParent(obj,3); 
}


function deleteByParent(obj, levels) {
	var parent = obj;
	var prev=null;
	
	for (var i = 0; i < levels; i++) {
	 	prev = parent;
	 	parent = 	parent.parentNode;
	}
	
	if (prev)  {
		//alert(parent.tagName + " is deleting an inner " + prev.tagName);
		parent.removeChild(prev);
	}
		
}

</script>
</head>
<body>
<#if (definition?exists && definition.name.length() > 0)>
<h1><@s.text name="title.createDefinition"/></h1>
	<#assign edit="true"/>
<#else>
<h1><@s.text name="title.createDefinition"/></h1>
</#if>
<form name="definitionForm" method="post" action="add.action">
<table>
	<tr>
		<td><@s.text name="label.job.name"/></td>
		<td><input type="text" name="definition.name" value="${(definition.name)!}"/> </td>
	</tr>
	<tr>
		<td><@s.text name="label.job.jobClass"/></td>
		<td><input type="text"  name="definition.className" value="${(definition.className)!}" /></td>
	</tr>
	<tr>
		<td><@s.text name="label.job.description"/></td>
		<td><textarea name="definition.description">${(definition.description)!}</textarea></td>
	</tr>
</table>
<input type="hidden" name="definitionName" value="${(definition.name)!}" />
<h3><@s.text name="label.definitions.params"/></h3>
<h3><@s.text name="title.createDefinitionJob.params"/></h3>
<table>
	<tr>
		<td><@s.text name="label.job.parameter.name"/></td>
		<td><@s.text name="label.job.parameter.description"/></td>
		<td><@s.text name="label.job.parameter.required"/></td>
	</tr>
<tr>
	<td><input type="hidden" name="definitionName" value="${(definition.name)!}"/><input id="parameter.name" type="text" name="parameter.name"/></td>
	<td><input type="text" id="parameter.description" name="parameter.description"/></td>
	<td><input type="checkbox" value="true" id="parameter.required" name="parameter.required"/>
	<input type="button" class="submit" name="cmdAddParameter" value="<@s.text name="label.add.parameter"/>" onclick="javascript:doAddParameter()" /></td>
</tr>
<tr><td>
<table id="paramterHolder">
<#if definition?exists>
<#assign params =definition.parameters/>
<#list params as p>
    <tr>
    	<td><input type="text" name="parameter.name"/>
		<input type="text" name="parameter.description"/>
		<input type="checkbox" value="true" name="parameter.required"/>${p.name}:  </td>
    	<td>${p.description}</td>
    	<td>${p.required?string!}  <a href="#" onclick="removeParameter(this)" ><@s.text name="label.global.remove"/></a></td>
    </tr>
</#list>
</#if>
</table>
</td></tr>
</table>
<input type="submit" class="submit" name="saveAction" value="<@s.text name="label.global.save"/>"/>
<input type="submit" class="submit" name="cancel" value="<@s.text name="label.cancel"/>"/>
</form>
<table  style="display: none">
  <tr id="proto" >
    <td><input type="hidden" name="parameter.name"/>
	<input type="hidden" name="parameter.description"/>
	<input type="hidden" value="true" name="parameter.required"/></td>
    	<td></td>
    	<td><a href="#" onclick="removeParameter(this)"><@s.text name="label.global.remove"/></a></td>
    </tr>
</table>
</body>
</html>
