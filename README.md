# GeoTrellis SEXP Based API

This is an example project that allows you to feed sexps into geotrellis
and get back results!

## Example

```lisp
(geotrellis.io.RenderPng
  (geotrellis.io.LoadRaster "SBN_car_share" null)
  (gtexample.GetColorBreaks
    (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster "SBN_car_share" null)))
  (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster "SBN_car_share" null))
  0)
```

Can be transformed into a service by running:

```scala
val op = Parser.toOp(Parser(S.sexp))
val result = Server.run(op)
```

## Services

The example server allows sexp-based services to be autoloaded.
The default autoload directory is "src/main/resources/services" and all
".svc" files will be loaded.

For example, if there was an svc file (say "render.svc") with the
following content:

```lisp
(defservice "simple-render")
(defparam "raster" "Name of the raster to load")

(geotrellis.io.RenderPng
  (geotrellis.io.LoadRaster (user-param "string" "raster") null)
  (gtexample.GetColorBreaks
    (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster (user-param "string" "raster") null)))
  (geotrellis.statistics.op.stat.GetHistogramMap
      (geotrellis.io.LoadRaster (user-param "string" "raster") null))
  0)
```

It will be loaded and wired up to:

```
http://localhost:8888/service/simple-render?raster=<some raster>
```

## Transformations

A baseline but buggy tree transformer attempts to
find and pre-fetch common subtrees in the parser. For example,
the sexp above ends up turning into:

```lisp
(set! $a (geotrellis.io.LoadRaster "SBN_car_share" null)
```

```lisp
(set! $b (geotrellis.statistics.op.stat.GetHistogramMap (var $a))
```

```lisp
(geotrellis.io.RenderPng (var $a)
  (gtexample.GetColorBreaks (var $b))
  (var $b))
  0)
```

This means that ```$a``` and ```$b``` are only run once.
