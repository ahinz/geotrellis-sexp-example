from geotrellis import *

raster = load_raster(user_param("string", "raster"))
hist = histogram(raster)
breaks = get_color_breaks(hist)
png = render_png(raster, breaks, hist)

service = Service("pyrender",[("raster","the raster to render")], png)

run_service(service)
