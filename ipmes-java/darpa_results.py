from darpa import darpa_graphs, pattern_file
import argparse
import json

def parse_cputime(stderr: str) -> float:
    lines = stderr.strip().split('\n')
    user_time = float(lines[-2].split()[1])
    sys_time = float(lines[-1].split()[1])
    return user_time + sys_time

def tabular_print(data: list[list]):
    for row in data:
        print('\t'.join(str(i) for i in row))

def flatten_print(data: list[list]):
    for row in data:
        print('\n'.join(str(i) for i in row))

print_methods = {
    'tabular': tabular_print,
    'flatten': flatten_print,
}

if __name__ == '__main__':
    parser = parser = argparse.ArgumentParser(
                    description='Print the DARPA run results')
    parser.add_argument('-t', '--cpu-time',
                        choices=print_methods.keys(),
                        default='flatten',
                        help='The method to print the measured CPU time')
    args = parser.parse_args()


    all_cputime = []
    all_usage_count: list[dict[int, int]] = []
    print('Results: [NumResults, PeekPoolSize]')
    for pattern_name, _ in pattern_file:
        cputime = []
        pattern_usage_count: dict[int, int] = {}
        for graph in darpa_graphs:
            stdout = open(f'../results/{pattern_name}_{graph}.out', 'r').read()
            stderr = open(f'../results/{pattern_name}_{graph}.err', 'r').read()
            cputime.append(parse_cputime(stderr))

            output = json.loads(stdout)
            usage_count: dict = output['UsageCount']
            for key, val in pattern_usage_count.items():
                key = int(key)
                count = pattern_usage_count.get(key, 0)
                pattern_usage_count[key] = count + val
            print('{}\t {}'.format(output['NumResults'], output['PeekPoolSize']))
        all_usage_count.append(pattern_usage_count)
        all_cputime.append(cputime)

    print('CPU Time (sec):')
    print_methods[args.cpu_time](all_cputime)

    print('TC-Query Trigger Count: [TCQueryLen, Count]')
    for pattern_usage_count in all_usage_count:
        for key, val in sorted(pattern_usage_count.items()):
            print('{}\t {}'.format(key, val))
