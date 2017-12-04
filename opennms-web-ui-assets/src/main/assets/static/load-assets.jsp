<%@ page contentType="text/html;charset=UTF-8" language="java" import="
java.util.*,
org.opennms.web.assets.api.*,
org.opennms.web.assets.impl.*,
org.slf4j.*
" %><%!
private static final Logger LOG = LoggerFactory.getLogger(AssetLocator.class);
%><%
final AssetLocator locator = AssetLocatorImpl.getInstance();

if (locator == null) {
    LOG.warn("load-assets.jsp is missing the locator");
} else {
    final String media = request.getParameter("media");
    final String mediaString = media == null? "" : " media=\"" + media + "\"";
    final String type = request.getParameter("type");
    final String[] assets = request.getParameterValues("asset");
    //if (LOG.isDebugEnabled()) LOG.debug("assets={}, type={}, media={}", Arrays.toString(assets), type, media);

    for (final String assetParam : assets) {
        LOG.debug("load-assets.jsp: asset={}, type={}, media={}", assetParam, type, media);
        final Collection<AssetResource> resources = locator.getResources(assetParam);
        if (resources == null) {
            LOG.warn("load-assets.jsp: resources not found for asset {}", assetParam);
        } else {
            for (final AssetResource resource : resources) {
                if (type != null && !type.equals(resource.getType())) {
                    LOG.trace("load-assets.jsp: skipping type {} for asset {}, page requested {}", resource.getType(), assetParam, type);
                    continue;
                }
                if ("js".equals(resource.getType())) {
                    out.write("<script src=\"assets/" + resource.getPath() + "\"></script>\n");
                } else if ("css".equals(resource.getType())) {
                    out.write("<link rel=\"stylesheet\" href=\"assets/" + resource.getPath() + "\"" + mediaString + ">\n");
                } else {
                    LOG.warn("load-assets.jsp: unknown/unhandled asset resource type: {}", resource.getType());
                }
            }
        }
    }
}
%>