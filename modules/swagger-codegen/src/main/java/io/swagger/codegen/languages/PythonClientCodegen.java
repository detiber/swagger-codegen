package io.swagger.codegen.languages;

import io.swagger.codegen.CliOption;
import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenConstants;
import io.swagger.codegen.CodegenModel;
import io.swagger.codegen.CodegenOperation;
import io.swagger.codegen.CodegenParameter;
import io.swagger.codegen.CodegenProperty;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.SupportingFile;
import io.swagger.models.Swagger;
import io.swagger.models.Tag;
import io.swagger.models.HttpMethod;
import io.swagger.models.Path;
import io.swagger.models.Operation;
import io.swagger.models.Model;
import io.swagger.models.properties.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.apache.commons.lang3.StringUtils;

public class PythonClientCodegen extends DefaultCodegen implements CodegenConfig {
    protected String packageName;
    protected String packageVersion;
    protected String apiDocPath = "docs/";
    protected String modelDocPath = "docs/";
    protected Map<Character, String> regexModifiers;
    protected Map<String, List<CodegenOperation>> pathOpMap;

	private String testFolder;

    public PythonClientCodegen() {
        super();

        modelPackage = "models";
        apiPackage = "api";
        outputFolder = "generated-code" + File.separatorChar + "python";

        modelTemplateFiles.put("model.mustache", ".py");
        apiTemplateFiles.put("api.mustache", ".py");

        modelTestTemplateFiles.put("model_test.mustache", ".py");
        apiTestTemplateFiles.put("api_test.mustache", ".py");

        embeddedTemplateDir = templateDir = "python";

        modelDocTemplateFiles.put("model_doc.mustache", ".md");
        apiDocTemplateFiles.put("api_doc.mustache", ".md");

        testFolder = "test";

        languageSpecificPrimitives.clear();
        languageSpecificPrimitives.add("int");
        languageSpecificPrimitives.add("float");
        languageSpecificPrimitives.add("list");
        languageSpecificPrimitives.add("bool");
        languageSpecificPrimitives.add("str");
        languageSpecificPrimitives.add("datetime");
        languageSpecificPrimitives.add("date");
        languageSpecificPrimitives.add("object");

        typeMapping.clear();
        typeMapping.put("integer", "int");
        typeMapping.put("float", "float");
        typeMapping.put("number", "float");
        typeMapping.put("long", "int");
        typeMapping.put("double", "float");
        typeMapping.put("array", "list");
        typeMapping.put("map", "dict");
        typeMapping.put("boolean", "bool");
        typeMapping.put("string", "str");
        typeMapping.put("date", "date");
        typeMapping.put("DateTime", "datetime");
        typeMapping.put("object", "object");
        typeMapping.put("file", "file");
        // TODO binary should be mapped to byte array
        // mapped to String as a workaround
        typeMapping.put("binary", "str");
        typeMapping.put("ByteArray", "str");
        // map uuid to string for the time being
        typeMapping.put("UUID", "str");

        // from https://docs.python.org/release/2.5.4/ref/keywords.html
        setReservedWordsLowerCase(
                Arrays.asList(
                    // local variable name used in API methods (endpoints)
                    "all_params", "resource_path", "path_params", "query_params",
                    "header_params", "form_params", "local_var_files", "body_params",  "auth_settings",
                    // @property
                    "property",
                    // python reserved words
                    "and", "del", "from", "not", "while", "as", "elif", "global", "or", "with",
                    "assert", "else", "if", "pass", "yield", "break", "except", "import",
                    "print", "class", "exec", "in", "raise", "continue", "finally", "is",
                    "return", "def", "for", "lambda", "try", "self"));

        regexModifiers = new HashMap<Character, String>();
        regexModifiers.put('i', "IGNORECASE");
        regexModifiers.put('l', "LOCALE");
        regexModifiers.put('m', "MULTILINE");
        regexModifiers.put('s', "DOTALL");
        regexModifiers.put('u', "UNICODE");
        regexModifiers.put('x', "VERBOSE");

        cliOptions.clear();
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_NAME, "python package name (convention: snake_case).")
                .defaultValue("swagger_client"));
        cliOptions.add(new CliOption(CodegenConstants.PACKAGE_VERSION, "python package version.")
                .defaultValue("1.0.0"));
        cliOptions.add(CliOption.newBoolean(CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG,
                CodegenConstants.SORT_PARAMS_BY_REQUIRED_FLAG_DESC).defaultValue(Boolean.TRUE.toString()));

        pathOpMap = new HashMap<String, List<CodegenOperation>>();
    }

    @Override
    public void processOpts() {
        super.processOpts();
        Boolean excludeTests = false;

        if(additionalProperties.containsKey(CodegenConstants.EXCLUDE_TESTS)) {
            excludeTests = Boolean.valueOf(additionalProperties.get(CodegenConstants.EXCLUDE_TESTS).toString());
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_NAME)) {
            setPackageName((String) additionalProperties.get(CodegenConstants.PACKAGE_NAME));
        }
        else {
            setPackageName("swagger_client");
        }

        if (additionalProperties.containsKey(CodegenConstants.PACKAGE_VERSION)) {
            setPackageVersion((String) additionalProperties.get(CodegenConstants.PACKAGE_VERSION));
        }
        else {
            setPackageVersion("1.0.0");
        }

        additionalProperties.put(CodegenConstants.PACKAGE_NAME, packageName);
        additionalProperties.put(CodegenConstants.PACKAGE_VERSION, packageVersion);

        // make api and model doc path available in mustache template
        additionalProperties.put("apiDocPath", apiDocPath);
        additionalProperties.put("modelDocPath", modelDocPath);

        String swaggerFolder = packageName;

        modelPackage = swaggerFolder + File.separatorChar + "models";
        apiPackage = swaggerFolder + File.separatorChar + "apis";

        supportingFiles.add(new SupportingFile("README.mustache", "", "README.md"));
        supportingFiles.add(new SupportingFile("LICENSE", "", "LICENSE"));

        supportingFiles.add(new SupportingFile("setup.mustache", "", "setup.py"));
        supportingFiles.add(new SupportingFile("tox.mustache", "", "tox.ini"));
        supportingFiles.add(new SupportingFile("test-requirements.mustache", "", "test-requirements.txt"));
        supportingFiles.add(new SupportingFile("requirements.mustache", "", "requirements.txt"));

        supportingFiles.add(new SupportingFile("api_client.mustache", swaggerFolder, "api_client.py"));
        supportingFiles.add(new SupportingFile("rest.mustache", swaggerFolder, "rest.py"));
        supportingFiles.add(new SupportingFile("configuration.mustache", swaggerFolder, "configuration.py"));
        supportingFiles.add(new SupportingFile("__init__package.mustache", swaggerFolder, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__model.mustache", modelPackage, "__init__.py"));
        supportingFiles.add(new SupportingFile("__init__api.mustache", apiPackage, "__init__.py"));

        if(Boolean.FALSE.equals(excludeTests)) {
            supportingFiles.add(new SupportingFile("__init__test.mustache", testFolder, "__init__.py"));
        }
        supportingFiles.add(new SupportingFile("git_push.sh.mustache", "", "git_push.sh"));
        supportingFiles.add(new SupportingFile("gitignore.mustache", "", ".gitignore"));
        supportingFiles.add(new SupportingFile("travis.mustache", "", ".travis.yml"));
    }

    private static String dropDots(String str) {
        return str.replaceAll("\\.", "_");
    }

    @Override
    public void postProcessParameter(CodegenParameter parameter){
        postProcessPattern(parameter.pattern, parameter.vendorExtensions);
    }

    @Override
    public void postProcessModelProperty(CodegenModel model, CodegenProperty property) {
        postProcessPattern(property.pattern, property.vendorExtensions);
    }

    /*
     * The swagger pattern spec follows the Perl convention and style of modifiers. Python
     * does not support this in as natural a way so it needs to convert it. See
     * https://docs.python.org/2/howto/regex.html#compilation-flags for details.
     */
    public void postProcessPattern(String pattern, Map<String, Object> vendorExtensions){
        if(pattern != null) {
            int i = pattern.lastIndexOf('/');

            //Must follow Perl /pattern/modifiers convention
            if(pattern.charAt(0) != '/' || i < 2) {
                throw new IllegalArgumentException("Pattern must follow the Perl "
                        + "/pattern/modifiers convention. "+pattern+" is not valid.");
            }

            String regex = pattern.substring(1, i).replace("'", "\'");
            List<String> modifiers = new ArrayList<String>();

            for(char c : pattern.substring(i).toCharArray()) {
                if(regexModifiers.containsKey(c)) {
                    String modifier = regexModifiers.get(c);
                    modifiers.add(modifier);
                }
            }

            vendorExtensions.put("x-regex", regex);
            vendorExtensions.put("x-modifiers", modifiers);
        }
    }


    @Override
    public Map<String, Object> postProcessModels(Map<String, Object> objs) {
        objs = super.postProcessModels(objs);


        String objs_package = (String) objs.get("package");
        List<String> objs_imports = (List<String>) objs.get("imports");
        List<Map<String, Object>>  objs_models = (List<Map<String, Object>>) objs.get("models");

        for(Map<String, Object>  model : objs_models) {
            CodegenModel cm = (CodegenModel) model.get("model");

            if (cm.name == "v1.Scale") continue;
            if (cm.name == "v1beta1.Scale") continue;

            String[] splitName = cm.name.split("\\.");
            if (splitName.length < 2) continue;

            String apiVersion = splitName[0];
            cm.vendorExtensions.put("apiVersion", apiVersion);
            cm.vendorExtensions.put("operations", new HashMap<String, HashMap<String, String>>());

            String unversionedName = splitName[1].toLowerCase();
            String unversionedPluralName = unversionedName + "s";
            for (String path : pathOpMap.keySet()) {
                List<String> pathParts = Arrays.asList(path.split("/"));

                if (pathParts.contains(apiVersion)) {
                    String matchedName = "";
                    if (pathParts.contains(unversionedName)) matchedName = unversionedName;
                    if (pathParts.contains(unversionedPluralName)) matchedName = unversionedPluralName;
                    if (matchedName == "") continue;

                    boolean nameMatch = false;
                    boolean lastMatch = false;

                    int nameIndex = pathParts.indexOf("{name}");
                    String lastPart = pathParts.get(pathParts.size() - 1);
                    if (lastPart.equals(matchedName)) {
                        lastMatch = true;
                    }
                    else if (nameIndex > 0 && pathParts.get(nameIndex - 1).equals(matchedName)) {
                        nameMatch = true;
                    }
                    else {
                        continue;
                    }

                    for (CodegenOperation cgop : pathOpMap.get(path)){
                        Map<String, String> method_info = new HashMap<String, String>();
                        method_info.put("method", cgop.operationId);
                        method_info.put("fileName", cgop.tags.get(0) + ".py");
                        method_info.put("className", toApiName(cgop.tags.get(0)));

                        String method_type = "";
                        if (pathParts.contains("{namespace}")) method_type += "namespaced_";

                        if (lastMatch && cgop.httpMethod.equals("POST")) {
                            method_type += "create";
                        }

                        if (nameMatch && pathParts.get(pathParts.size() - 1).equals("{name}")){
                            if (cgop.httpMethod.equals("DELETE")) {
                                method_type += "delete";
                            }
                            else if (cgop.httpMethod.equals("PUT")) {
                                method_type += "replace";
                            }
                            else if (cgop.httpMethod.equals("POST")) {
                                method_type += "create";
                            }
                            else if (cgop.httpMethod.equals("PATCH")) {
                                method_type += "patch";
                            }
                        }


                        if (!method_type.equals("") && !method_type.equals("namespaced_")) {
                            Map<String, Map<String, String>> operations = (Map<String, Map<String, String>>) cm.vendorExtensions.get("operations");
                            operations.put(method_type, method_info);
                            cm.vendorExtensions.put("operations", operations);
                        }

                    }
                }
            }
        }

        return objs;
    }


    @Override
    public void preprocessSwagger(Swagger swagger) {
        Map<String, Path> paths = swagger.getPaths();

        Pattern versionPattern = Pattern.compile("^(.*)(v\\d+(?:(?:alpha|beta)\\d+)?)(.*)");
        ArrayList<String> pathsToDelete = new ArrayList<>();
        for (Map.Entry<String, Path> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Path pathObject = pathEntry.getValue();

            Matcher versionMatcher = versionPattern.matcher(path);
            Map<HttpMethod, Operation> opMap = pathObject.getOperationMap();
            for (Map.Entry<HttpMethod, Operation> opEntry : opMap.entrySet()){

                HttpMethod method = opEntry.getKey();
                Operation op = opEntry.getValue();

                List<String> tags = op.getTags();

                String pathTag = path.replace("/", "");
                if (versionMatcher.matches()) {
                    pathTag = (versionMatcher.group(1) + versionMatcher.group(2)).replace("/", "");
                }

                int pathIndex = tags.indexOf(pathTag);
                if (pathIndex >= 0) {
                    if (versionMatcher.matches()){
                        tags.set(pathIndex, (versionMatcher.group(1) + versionMatcher.group(2)).replace("/", "_").substring(1));
                    }
                    else {
                        tags.set(pathIndex, path.replace("/", "_").substring(1));
                    }
                }

                if (versionMatcher.matches()) {
                    String newOpId = "";
                    ArrayList<String> pathComponents = new ArrayList(Arrays.asList(versionMatcher.group(3).split("/")));
                    if (pathComponents.contains("proxy")){
                        // TODO: handle proxy paths rather than deleting them
                        pathsToDelete.add(path);
                    }
                    else {
                        String lastComponent = pathComponents.get(pathComponents.size() - 1);
                        if (Arrays.asList("watch", "status", "attach", "exec", "portforward", "binding", "bindings").contains(lastComponent)){
                            // TODO: handle special paths rather than deleting them
                            pathsToDelete.add(path);
                        }
                        else {
                            switch (method) {
                                case GET:
                                    if (pathComponents.contains("watch")) {
                                        newOpId = "watch";
                                    }
                                    else if (pathComponents.contains("{name}")){
                                        newOpId = "get";
                                    }
                                    else {
                                        newOpId = "list";
                                    }
                                    break;
                                case POST:
                                    newOpId = "create";
                                    break;
                                case PUT:
                                    newOpId = "replace";
                                    break;
                                case PATCH:
                                    newOpId = "patch";
                                    break;
                                case DELETE:
                                    newOpId = "delete";
                                    break;
                            }


                            if (pathComponents.contains("{name}")) {
                                int index = pathComponents.indexOf("{name}");
                                pathComponents.remove(index);
                                int prevItem = index - 1;
                                String prevComponent = pathComponents.get(prevItem);
                                if (prevComponent.endsWith("ses")){
                                    pathComponents.set(prevItem, prevComponent.substring(0, prevComponent.length() - 2));
                                }
                                else if (prevComponent.endsWith("s")){
                                    pathComponents.set(prevItem, prevComponent.substring(0, prevComponent.length() - 1));
                                }
                            }

                            if (method.toString() == "POST"){
                                // retrieve lastComponent again, since we
                                // might have changed the size of the List
                                int lastItem = pathComponents.size() - 1;
                                lastComponent = pathComponents.get(lastItem);
                                if (lastComponent.endsWith("ses")){
                                    pathComponents.set(lastItem, lastComponent.substring(0, lastComponent.length() - 2));
                                }
                                else if (lastComponent.endsWith("s")){
                                    pathComponents.set(lastItem, lastComponent.substring(0, lastComponent.length() - 1));
                                }
                            }

                            if (pathComponents.contains("{namespace}")) {
                                newOpId += "Namespaced";
                                pathComponents.remove("namespaces");
                                pathComponents.remove("{namespace}");
                            }

                            for (String item : pathComponents){
                                newOpId += StringUtils.capitalize(item);
                            }
                            op.setOperationId(newOpId);
                        }
                    }
                }
            }
        }

        for (String path : pathsToDelete){
            paths.remove(path);
            swagger.setPaths(paths);
        }

        for (Map.Entry<String, Path> pathEntry : paths.entrySet()) {
            String path = pathEntry.getKey();
            Path pathObject = pathEntry.getValue();

            Map<HttpMethod, Operation> opMap = pathObject.getOperationMap();
            for (Map.Entry<HttpMethod, Operation> opEntry : opMap.entrySet()){
                HttpMethod method = opEntry.getKey();
                Operation op = opEntry.getValue();

				CodegenOperation cgop = fromOperation(path, method.toString(), op, swagger.getDefinitions(), swagger);
                if (! pathOpMap.containsKey(path)) {
                    pathOpMap.put(path, new ArrayList<CodegenOperation>());
                }
                pathOpMap.get(path).add(cgop);

    }

    @Override
    public String sanitizeTag(String tag) {
        // remove spaces and make strong case
        String[] parts = tag.split(" ");
        StringBuilder buf = new StringBuilder();
        for (String part : parts) {
            if (StringUtils.isNotEmpty(part)) {
                buf.append(StringUtils.capitalize(part));
            }
        }
        String result = buf.toString().replaceAll("[\\W ]", "");
        return result;
    }


    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "python";
    }

    @Override
    public String getHelp() {
        return "Generates a Python client library.";
    }

    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;
    }

    @Override
    public String apiDocFileFolder() {
        return (outputFolder + "/" + apiDocPath);
    }

    @Override
    public String modelDocFileFolder() {
        return (outputFolder + "/" + modelDocPath);
    }

    @Override
    public String toModelDocFilename(String name) {
        return toModelName(name);
    }

    @Override
    public String toApiDocFilename(String name) {
        return toApiName(name);
    }


    @Override
    public String apiFileFolder() {
        return outputFolder + File.separatorChar + apiPackage().replace('.', File.separatorChar);
    }

    @Override
    public String modelFileFolder() {
        return outputFolder + File.separatorChar + modelPackage().replace('.', File.separatorChar);
    }

    @Override
    public String apiTestFileFolder() {
    	return outputFolder + File.separatorChar + testFolder;
    }

    @Override
    public String modelTestFileFolder() {
    	return outputFolder + File.separatorChar + testFolder;
    }

    @Override
    public String getTypeDeclaration(Property p) {
        if (p instanceof ArrayProperty) {
            ArrayProperty ap = (ArrayProperty) p;
            Property inner = ap.getItems();
            return getSwaggerType(p) + "[" + getTypeDeclaration(inner) + "]";
        } else if (p instanceof MapProperty) {
            MapProperty mp = (MapProperty) p;
            Property inner = mp.getAdditionalProperties();

            return getSwaggerType(p) + "(str, " + getTypeDeclaration(inner) + ")";
        }
        return super.getTypeDeclaration(p);
    }

    @Override
    public String getSwaggerType(Property p) {
        String swaggerType = super.getSwaggerType(p);
        String type = null;
        if (typeMapping.containsKey(swaggerType)) {
            type = typeMapping.get(swaggerType);
            if (languageSpecificPrimitives.contains(type)) {
                return type;
            }
        } else {
            type = toModelName(swaggerType);
        }
        return type;
    }

    @Override
    public String toVarName(String name) {
        // sanitize name
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.

        // remove dollar sign
        name = name.replaceAll("$", "");

        // if it's all uppper case, convert to lower case
        if (name.matches("^[A-Z_]*$")) {
            name = name.toLowerCase();
        }

        // underscore the variable name
        // petId => pet_id
        name = underscore(name);

        // remove leading underscore
        name = name.replaceAll("^_*", "");

        // for reserved word or word starting with number, append _
        if (isReservedWord(name) || name.matches("^\\d.*")) {
            name = escapeReservedWord(name);
        }

        return name;
    }

    @Override
    public String toParamName(String name) {
        // should be the same as variable name
        return toVarName(name);
    }

    @Override
    public String toModelName(String name) {
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.
        // remove dollar sign
        name = name.replaceAll("$", "");

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model name. Renamed to " + camelize("model_" + name));
            name = "model_" + name; // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + camelize("model_" + name));
            name = "model_" + name; // e.g. 200Response => Model200Response (after camelize)
        }

        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix;
        }

        // camelize the model name
        // phone_number => PhoneNumber
        return camelize(name);
    }

    @Override
    public String toModelFilename(String name) {
        name = sanitizeName(name); // FIXME: a parameter should not be assigned. Also declare the methods parameters as 'final'.
        // remove dollar sign
        name = name.replaceAll("$", "");

        // model name cannot use reserved keyword, e.g. return
        if (isReservedWord(name)) {
            LOGGER.warn(name + " (reserved word) cannot be used as model filename. Renamed to " + underscore(dropDots("model_" + name)));
            name = "model_" + name; // e.g. return => ModelReturn (after camelize)
        }

        // model name starts with number
        if (name.matches("^\\d.*")) {
            LOGGER.warn(name + " (model name starts with number) cannot be used as model name. Renamed to " + underscore("model_" + name));
            name = "model_" + name; // e.g. 200Response => Model200Response (after camelize)
        }

        if (!StringUtils.isEmpty(modelNamePrefix)) {
            name = modelNamePrefix + "_" + name;
        }

        if (!StringUtils.isEmpty(modelNameSuffix)) {
            name = name + "_" + modelNameSuffix;
        }

        // underscore the model file name
        // PhoneNumber => phone_number
        return underscore(dropDots(name));
    }

    @Override
    public String toModelTestFilename(String name) {
    	return "test_" + toModelFilename(name);
    };

    @Override
    public String toApiFilename(String name) {
        // replace - with _ e.g. created-at => created_at
        name = name.replaceAll("-", "_");

        // e.g. PhoneNumberApi.rb => phone_number_api.rb
        return underscore(name);
    }

    @Override
    public String toApiTestFilename(String name) {
    	return "test_" + toApiFilename(name);
    }

    @Override
    public String toApiName(String name) {
        if (name.length() == 0) {
            return "DefaultApi";
        }
        // e.g. phone_number_api => PhoneNumberApi
        return camelize(name);
    }

    @Override
    public String toApiVarName(String name) {
        if (name.length() == 0) {
            return "default_api";
        }
        return underscore(name);
    }

    @Override
    public String toOperationId(String operationId) {
        // throw exception if method name is empty (should not occur as an auto-generated method name will be used)
        if (StringUtils.isEmpty(operationId)) {
            throw new RuntimeException("Empty method name (operationId) not allowed");
        }

        // method name cannot use reserved keyword, e.g. return
        if (isReservedWord(operationId)) {
            LOGGER.warn(operationId + " (reserved word) cannot be used as method name. Renamed to " + underscore(sanitizeName("call_" + operationId)));
            operationId = "call_" + operationId;
        }

        return underscore(sanitizeName(operationId));
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    /**
     * Generate Python package name from String `packageName`
     *
     * (PEP 0008) Python packages should also have short, all-lowercase names,
     * although the use of underscores is discouraged.
     *
     * @param packageName Package name
     * @return Python package name that conforms to PEP 0008
     */
    @SuppressWarnings("static-method")
    public String generatePackageName(String packageName) {
        return underscore(packageName.replaceAll("[^\\w]+", ""));
    }

    /**
     * Return the default value of the property
     *
     * @param p Swagger property object
     * @return string presentation of the default value of the property
     */
    @Override
    public String toDefaultValue(Property p) {
        if (p instanceof StringProperty) {
            StringProperty dp = (StringProperty) p;
            if (dp.getDefault() != null) {
                return "'" + dp.getDefault().toString() + "'";
            }
        } else if (p instanceof BooleanProperty) {
            BooleanProperty dp = (BooleanProperty) p;
            if (dp.getDefault() != null) {
                if (dp.getDefault().toString().equalsIgnoreCase("false"))
                    return "False";
                else
                    return "True";
            }
        } else if (p instanceof DateProperty) {
            // TODO
        } else if (p instanceof DateTimeProperty) {
            // TODO
        } else if (p instanceof DoubleProperty) {
            DoubleProperty dp = (DoubleProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof FloatProperty) {
            FloatProperty dp = (FloatProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof IntegerProperty) {
            IntegerProperty dp = (IntegerProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        } else if (p instanceof LongProperty) {
            LongProperty dp = (LongProperty) p;
            if (dp.getDefault() != null) {
                return dp.getDefault().toString();
            }
        }

        return null;
    }

    @Override
    public void setParameterExampleValue(CodegenParameter p) {
        String example;

        if (p.defaultValue == null) {
            example = p.example;
        } else {
            example = p.defaultValue;
        }

        String type = p.baseType;
        if (type == null) {
            type = p.dataType;
        }

        if ("String".equalsIgnoreCase(type) || "str".equalsIgnoreCase(type)) {
            if (example == null) {
                example = p.paramName + "_example";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("Integer".equals(type) || "int".equals(type)) {
            if (example == null) {
                example = "56";
            }
        } else if ("Float".equalsIgnoreCase(type) || "Double".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "3.4";
            }
        } else if ("BOOLEAN".equalsIgnoreCase(type) || "bool".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "True";
            }
        } else if ("file".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "/path/to/file";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("Date".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "2013-10-20";
            }
            example = "'" + escapeText(example) + "'";
        } else if ("DateTime".equalsIgnoreCase(type)) {
            if (example == null) {
                example = "2013-10-20T19:20:30+01:00";
            }
            example = "'" + escapeText(example) + "'";
        } else if (!languageSpecificPrimitives.contains(type)) {
            // type is a model class, e.g. User
            example = this.packageName + "." + type + "()";
        } else {
            LOGGER.warn("Type " + type + " not handled properly in setParameterExampleValue");
        }

        if (example == null) {
            example = "NULL";
        } else if (Boolean.TRUE.equals(p.isListContainer)) {
            example = "[" + example + "]";
        } else if (Boolean.TRUE.equals(p.isMapContainer)) {
            example = "{'key': " + example + "}";
        }

        p.example = example;
    }

    @Override
    public String escapeQuotationMark(String input) {
        // remove ' to avoid code injection
        return input.replace("'", "");
    }

    @Override
    public String escapeUnsafeCharacters(String input) {
        // remove multiline comment
        return input.replace("'''", "'_'_'");
    }

}
