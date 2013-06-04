#
# Geotrellis Python Service Bindings
#

class Operation(object):
    def __init__(self, operation, args):
        self.operation = operation
        self.args = args

    def tosexp(self):
        parts = [self.operation]
        for a in self.args:
            if a == "null":
                s = "null"
            elif isinstance(a, basestring):
                s = '"%s"' % a.replace('"', '\\"')
            elif isinstance(a, Operation):
                s = a.tosexp()
            else:
                s = str(a)

            parts.append(s)

        return '(%s)' % ' '.join(parts)

class Service(object):
    def __init__(self, name, args, op):
        self.name = name
        self.args = args
        self.op = op

    def tosexp(self):
        output = '(defservice "%s")\n' % self.name
        for arg in self.args:
            output += '(defparam "%s" "%s")\n' % arg

        output += '\n'
        output += self.op.tosexp()

        return output

def _make_operation(name, pfx_args=[], sfx_args=[]):
    def operation(*args):
        return Operation(name, pfx_args + list(args) + sfx_args)

    return operation

render_png = _make_operation('geotrellis.io.RenderPng', [], [0])
get_color_breaks = _make_operation('gtexample.GetColorBreaks')
load_raster = _make_operation('geotrellis.io.LoadRaster', [], ["null"])
user_param = _make_operation('user-param')
histogram = _make_operation('geotrellis.statistics.op.stat.GetHistogramMap')

def run_service(s):
    print s.tosexp()
